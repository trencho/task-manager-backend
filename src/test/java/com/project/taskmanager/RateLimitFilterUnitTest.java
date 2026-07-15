package com.project.taskmanager;

import java.time.Duration;
import jakarta.servlet.FilterChain;

import com.project.taskmanager.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The capacity is injected, so exhausting a bucket takes two requests rather than five and no
 * test ever sleeps waiting for a refill.
 */
class RateLimitFilterUnitTest {

    private static final int CAPACITY = 2;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    private static final int MAX_BUCKETS = 100;

    private final FilterChain filterChain = mock(FilterChain.class);

    private RateLimitFilter filter(final boolean trustForwardedFor) {
        return new RateLimitFilter(CAPACITY, REFILL_PERIOD, trustForwardedFor, MAX_BUCKETS);
    }

    private MockHttpServletRequest post(final String uri) {
        final var request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void shouldRejectTheRequestThatExceedsTheCapacity() throws Exception {
        final var rateLimitFilter = filter(false);

        for (var i = 0; i < CAPACITY; i++) {
            final var response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(post("/api/auth/login"), response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        final var rejected = new MockHttpServletResponse();
        rateLimitFilter.doFilter(post("/api/auth/login"), rejected, filterChain);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(rejected.getHeader("Retry-After"))).isPositive();
        // The chain ran for the allowed requests only: the rejected one never reached the controller.
        verify(filterChain, times(CAPACITY)).doFilter(any(), any());
    }

    @Test
    void shouldRateLimitSignupAsWellAsLogin() throws Exception {
        final var rateLimitFilter = filter(false);

        for (var i = 0; i < CAPACITY; i++) {
            rateLimitFilter.doFilter(post("/api/auth/signup"), new MockHttpServletResponse(), filterChain);
        }
        final var rejected = new MockHttpServletResponse();
        rateLimitFilter.doFilter(post("/api/auth/signup"), rejected, filterChain);

        assertThat(rejected.getStatus()).isEqualTo(429);
    }

    /**
     * login and signup must not share a bucket -- exhausting one would lock out the other.
     */
    @Test
    void shouldNotShareABucketBetweenPaths() throws Exception {
        final var rateLimitFilter = filter(false);

        for (var i = 0; i < CAPACITY; i++) {
            rateLimitFilter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), filterChain);
        }

        final var signup = new MockHttpServletResponse();
        rateLimitFilter.doFilter(post("/api/auth/signup"), signup, filterChain);

        assertThat(signup.getStatus()).isEqualTo(200);
    }

    /**
     * A filter that throttled the wrong routes would 429 legitimate traffic. Only POST to the two
     * credential endpoints is limited.
     */
    @Test
    void shouldNotFilterAnyOtherRoute() throws Exception {
        final var rateLimitFilter = filter(false);

        for (final var uri : new String[] { "/api/auth/refresh-token", "/api/auth/logout", "/api/auth/logout-all",
                "/api/tasks", "/api/auth/login/extra" }) {
            for (var i = 0; i < CAPACITY + 1; i++) {
                final var response = new MockHttpServletResponse();
                rateLimitFilter.doFilter(post(uri), response, filterChain);
                assertThat(response.getStatus()).as(uri).isEqualTo(200);
            }
        }
    }

    @Test
    void shouldNotRateLimitANonPostRequestToALimitedPath() throws Exception {
        final var rateLimitFilter = filter(false);

        for (var i = 0; i < CAPACITY + 1; i++) {
            final var request = new MockHttpServletRequest("GET", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            final var response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void shouldKeepASeparateBucketPerClientAddress() throws Exception {
        final var rateLimitFilter = filter(false);

        for (var i = 0; i < CAPACITY; i++) {
            rateLimitFilter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), filterChain);
        }

        final var other = post("/api/auth/login");
        other.setRemoteAddr("10.0.0.2");
        final var response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(other, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * The header is a free-text field any client can set. Unless the deployment says a proxy is
     * rewriting it, it must not become the key -- otherwise a caller varies it per request and the
     * limiter never fires.
     */
    @Test
    void shouldIgnoreForwardedForWhenItIsNotTrusted() throws Exception {
        final var rateLimitFilter = filter(false);

        MockHttpServletResponse response = null;
        for (var i = 0; i < CAPACITY + 1; i++) {
            final var request = post("/api/auth/login");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        assertThat(response.getStatus()).isEqualTo(429);
        verify(filterChain, times(CAPACITY)).doFilter(any(), any());
    }

    @Test
    void shouldKeyOnTheFirstForwardedForHopWhenTrusted() throws Exception {
        final var rateLimitFilter = filter(true);

        // Same remoteAddr (the proxy), different real clients: neither exhausts the other's bucket.
        for (var i = 0; i < CAPACITY + 1; i++) {
            final var request = post("/api/auth/login");
            request.addHeader("X-Forwarded-For", "203.0.113." + i + ", 198.51.100.7");
            final var response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // ...and the same real client, arriving through the proxy, is still limited.
        MockHttpServletResponse response = null;
        for (var i = 0; i < CAPACITY + 1; i++) {
            final var request = post("/api/auth/login");
            request.addHeader("X-Forwarded-For", "203.0.113.50, 198.51.100.7");
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
        }
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldFallBackToRemoteAddrWhenTrustedButTheHeaderIsAbsent() throws Exception {
        final var rateLimitFilter = filter(true);

        MockHttpServletResponse response = null;
        for (var i = 0; i < CAPACITY + 1; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(post("/api/auth/login"), response, filterChain);
        }

        assertThat(response.getStatus()).isEqualTo(429);
    }

    /**
     * The key is client-controlled, so the map must not grow without bound. Evicting the eldest
     * entry is what keeps it finite; the cost is that a flood of fresh keys can evict a real
     * client's bucket, which then simply starts full.
     */
    @Test
    void shouldBoundTheNumberOfBuckets() throws Exception {
        final var rateLimitFilter = new RateLimitFilter(CAPACITY, REFILL_PERIOD, false, 2);

        // Exhaust 10.0.0.1, then push two further addresses through to evict it.
        for (var i = 0; i < CAPACITY; i++) {
            rateLimitFilter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), filterChain);
        }
        for (final var addr : new String[] { "10.0.0.2", "10.0.0.3" }) {
            final var request = post("/api/auth/login");
            request.setRemoteAddr(addr);
            rateLimitFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        // Its bucket was evicted, so it is served again rather than remembered forever.
        final var response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(post("/api/auth/login"), response, filterChain);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldNotInvokeTheChainForARejectedRequest() throws Exception {
        final var rateLimitFilter = filter(false);
        final var chain = mock(FilterChain.class);

        for (var i = 0; i < CAPACITY; i++) {
            rateLimitFilter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(CAPACITY)).doFilter(any(), any());

        // never() counts every invocation ever recorded, not just the ones after this line.
        clearInvocations(chain);
        rateLimitFilter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), chain);
        verify(chain, never()).doFilter(any(), any());
    }
}

package com.project.taskmanager.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the two unauthenticated credential endpoints, which are otherwise free to brute-force.
 * <p>
 * The buckets live in memory, so the limit is per instance and resets on restart. That is correct
 * for this single-instance deployment and wrong for a horizontally scaled one -- bucket4j's
 * distributed backends exist for that case.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/signup");
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final int capacity;
    private final Duration refillPeriod;
    private final boolean trustForwardedFor;

    /**
     * LRU-bounded. bucket4j evicts nothing on its own, and the key is client-controlled, so an
     * unbounded map is a memory-exhaustion vector: one request per forged address grows it forever.
     */
    private final Map<String, Bucket> buckets;

    public RateLimitFilter(
            final int capacity, final Duration refillPeriod, final boolean trustForwardedFor, final int maxBuckets) {
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.trustForwardedFor = trustForwardedFor;
        this.buckets = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Bucket> eldest) {
                return size() > maxBuckets;
            }
        });
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !RATE_LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        // Keyed on path as well as client: exhausting the login allowance must not also lock the
        // caller out of signing up.
        final var key = request.getRequestURI() + '|' + clientKey(request);
        final var probe = buckets.computeIfAbsent(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Round up: a sub-second wait must not be advertised as "retry immediately".
        final var retryAfterSeconds =
                Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"message\":\"Too many requests. Try again in %d seconds.\"}".formatted(retryAfterSeconds));
    }

    private Bucket newBucket() {
        // refillIntervally, not refillGreedy: the whole allowance returns at once at the end of the
        // period. Greedy would trickle a token back every period/capacity, which lets a patient
        // caller sustain exactly the limit forever -- fine for an API quota, pointless for a
        // brute-force guard.
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, refillPeriod)
                        .build())
                .build();
    }

    /**
     * Behind a reverse proxy every request appears to come from the proxy, so one client's
     * hammering would throttle everyone. {@code X-Forwarded-For} fixes that -- but any client can
     * send that header, so trusting it unconditionally hands attackers an unlimited supply of keys
     * and disables the limiter entirely. It is therefore opt-in, and only ever the first hop.
     */
    private String clientKey(final HttpServletRequest request) {
        if (trustForwardedFor) {
            final var forwardedFor = request.getHeader(X_FORWARDED_FOR);
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

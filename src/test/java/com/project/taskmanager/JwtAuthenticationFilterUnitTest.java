package com.project.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.security.CustomUserDetails;
import com.project.taskmanager.security.CustomUserDetailsService;
import com.project.taskmanager.security.JwtAuthenticationFilter;
import com.project.taskmanager.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterUnitTest {

    private static final String TOKEN = "a.b.c";
    private static final String USERNAME = "username";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * The SecurityContext is a thread-local. TaskControllerIntegrationTest installs a mocked
     * context and never clears it, so without the @BeforeEach these tests inherit a mock
     * Authentication and the "no token" assertions fail. Clear on both sides: don't inherit,
     * don't leak.
     */
    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static CustomUserDetails userDetails() {
        final var user = new User();
        user.setUsername(USERNAME);
        user.setPassword("hashed");
        user.setRoles(Set.of("USER"));
        return new CustomUserDetails(user);
    }

    @Test
    void shouldAuthenticateWhenTheTokenIsValid() throws Exception {
        when(jwtTokenProvider.resolveToken(request)).thenReturn(TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(TOKEN)).thenReturn(USERNAME);
        when(customUserDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails());

        filter.doFilter(request, response, filterChain);

        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(USERNAME);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("USER");
        assertThat(authentication.getDetails()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    /**
     * An anonymous request must pass straight through. Loading a user for a request that carries
     * no token would be a database hit on every public endpoint.
     */
    @Test
    void shouldNotAuthenticateWhenThereIsNoToken() throws Exception {
        when(jwtTokenProvider.resolveToken(request)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(customUserDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * A forged or expired token must leave the context anonymous — and must not short-circuit the
     * chain, or the request would hang instead of reaching the entry point that returns 401.
     */
    @Test
    void shouldNotAuthenticateWhenTheTokenIsInvalid() throws Exception {
        when(jwtTokenProvider.resolveToken(request)).thenReturn(TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(customUserDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }
}

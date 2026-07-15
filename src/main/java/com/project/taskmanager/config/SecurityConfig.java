package com.project.taskmanager.config;

import java.time.Duration;

import com.project.taskmanager.security.CustomUserDetailsService;
import com.project.taskmanager.security.JwtAuthenticationFilter;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.rate-limit.capacity:5}")
    private int rateLimitCapacity;

    @Value("${app.rate-limit.refill-period:1m}")
    private Duration rateLimitRefillPeriod;

    /**
     * Off by default: X-Forwarded-For is only meaningful when a proxy you control rewrites it.
     * Turn it on in the deployment that actually runs behind nginx, and nowhere else.
     */
    @Value("${app.rate-limit.trust-forwarded-for:false}")
    private boolean rateLimitTrustForwardedFor;

    @Value("${app.rate-limit.max-buckets:10000}")
    private int rateLimitMaxBuckets;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(requests -> requests
                // Ahead of the /api/auth/** permitAll below: first match wins. Revoking
                // every session is authenticated, because no token is presented to
                // establish authority -- unlike /logout, where possession of the refresh
                // token is the authority to revoke it.
                .requestMatchers(HttpMethod.POST, "/api/auth/logout-all").authenticated()
                .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                        // Both forms: "/v3/api-docs/**" does not match the bare
                        // "/v3/api-docs", which is the path springdoc actually serves.
                        "/v3/api-docs", "/v3/api-docs/**", "/webjars/**")
                .permitAll().anyRequest().authenticated())
                // No .logout(...) customizer. Spring Security's LogoutFilter would intercept
                // POST /api/auth/logout before it reached AuthController, answering 302 to a
                // login URL. Its work -- invalidating an HTTP session, clearing JSESSIONID --
                // is meaningless here: the session policy is STATELESS and auth is a bearer
                // token. Revoking the refresh token is the only thing logout can actually do,
                // and AuthController does it.
                .exceptionHandling(exceptionHandlingCustomizer -> exceptionHandlingCustomizer
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                // Ahead of the JWT filter: throttling a credential-guessing flood must not depend
                // on any work done for a token the caller does not have.
                .addFilterBefore(rateLimitFilter(), JwtAuthenticationFilter.class)
                .sessionManagement(sessionManagementCustomizer -> sessionManagementCustomizer
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimitCapacity, rateLimitRefillPeriod, rateLimitTrustForwardedFor,
                rateLimitMaxBuckets);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Spring Security 6.5 deprecated the no-arg constructor and setUserDetailsService
        // in favour of supplying the service up front.
        final var authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}

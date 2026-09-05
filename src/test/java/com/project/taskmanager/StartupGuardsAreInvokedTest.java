package com.project.taskmanager;

import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.security.RefreshTokenCookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Spring actually INVOKES the two startup guards, not merely that their predicates work.
 *
 * <p>Found by mutation: deleting {@code @PostConstruct} from either
 * {@code JwtTokenProvider.rejectCompromisedSecret()} or
 * {@code RefreshTokenCookie.rejectUnsafeSameSite()} passed all 128 tests. Both guards are carefully
 * written and their predicates are well covered -- by tests that call the method directly, which
 * proves the predicate and says nothing about the trigger. Remove one annotation and the guard is
 * silently inert, which is precisely the failure both guards exist to prevent, one level up.
 *
 * <p>These boot a real container and assert it REFUSES to start, so the annotation is load-bearing.
 *
 * <p>The values used here are safe to write down. {@code your_jwt_secret_key} is a placeholder that
 * was never a key, and {@code None} is a configuration value rather than a credential. The real
 * 64-character secret is never needed: {@code JwtTokenProviderUnitTest} covers it by digest.
 */
class StartupGuardsAreInvokedTest {

    private static final String GOOD_SECRET = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class));

    @Test
    void springInvokesTheCompromisedSecretGuardAtStartup() {
        runner.withUserConfiguration(JwtOnly.class)
                .withPropertyValues("jwt.secret=your_jwt_secret_key", "jwt.accessTokenExpiration=3600000",
                        "jwt.refreshTokenExpiration=86400000")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("published"));
    }

    @Test
    void aGoodSecretStartsNormally() {
        // Without this, a guard that rejected EVERY secret would pass the test above. A guard that
        // blocks everything passes every safety check.
        runner.withUserConfiguration(JwtOnly.class)
                .withPropertyValues("jwt.secret=" + GOOD_SECRET, "jwt.accessTokenExpiration=3600000",
                        "jwt.refreshTokenExpiration=86400000")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class));
    }

    @Test
    void springInvokesTheSameSiteGuardAtStartup() {
        runner.withUserConfiguration(CookieOnly.class)
                .withPropertyValues("jwt.refreshCookie.name=t", "jwt.refreshCookie.path=/api/auth",
                        "jwt.refreshCookie.sameSite=None", "jwt.refreshCookie.secure=true",
                        "jwt.refreshTokenExpiration=86400000")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("cross-site"));
    }

    @Test
    void aSafeSameSiteStartsNormally() {
        runner.withUserConfiguration(CookieOnly.class)
                .withPropertyValues("jwt.refreshCookie.name=t", "jwt.refreshCookie.path=/api/auth",
                        "jwt.refreshCookie.sameSite=Strict", "jwt.refreshCookie.secure=true",
                        "jwt.refreshTokenExpiration=86400000")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RefreshTokenCookie.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class JwtOnly {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CookieOnly {

        @Bean
        RefreshTokenCookie refreshTokenCookie() {
            return new RefreshTokenCookie();
        }
    }
}

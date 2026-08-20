package com.project.taskmanager.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the startup guard on {@code SameSite}, and the cookie-reading branches that had none.
 *
 * <p>The guard exists because {@code SecurityConfig} disables CSRF protection outright and
 * {@code SameSite} is the only thing standing in its place. That is a correct design while the
 * value is {@code Strict} or {@code Lax} -- and it is one injected string away from being wrong,
 * with no error attached to getting it wrong. The README documents a cross-origin deployment in
 * which a {@code Strict} cookie is never sent, so an operator hitting that wall has exactly one
 * obvious knob to turn.
 */
class RefreshTokenCookieTest {

    private static RefreshTokenCookie cookieWith(final String sameSite) {
        final var component = new RefreshTokenCookie();
        ReflectionTestUtils.setField(component, "name", "refresh_token");
        ReflectionTestUtils.setField(component, "path", "/api/auth");
        ReflectionTestUtils.setField(component, "sameSite", sameSite);
        ReflectionTestUtils.setField(component, "secure", true);
        ReflectionTestUtils.setField(component, "refreshTokenExpiration", 604800000L);
        return component;
    }

    // --- the startup guard --------------------------------------------------------------------

    @Test
    void refusesToStartWhenSameSiteIsNone() {
        assertThatThrownBy(() -> cookieWith("None").rejectUnsafeSameSite()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cross-site request forgery");
    }

    /** Case must not be a way past it: `none`, `None` and `NONE` are the same setting. */
    @Test
    void refusesLowercaseAndPaddedNoneToo() {
        assertThatThrownBy(() -> cookieWith("none").rejectUnsafeSameSite()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cookieWith("  NONE  ").rejectUnsafeSameSite())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrictAndLax() {
        assertThatCode(() -> cookieWith("Strict").rejectUnsafeSameSite()).doesNotThrowAnyException();
        assertThatCode(() -> cookieWith("Lax").rejectUnsafeSameSite()).doesNotThrowAnyException();
    }

    /**
     * A guard that dies on an unset value would turn a missing property into a confusing CSRF
     * error rather than the missing-property error it is. Spring reports that on its own.
     */
    @Test
    void ignoresAnUnsetValue() {
        assertThatCode(() -> cookieWith(null).rejectUnsafeSameSite()).doesNotThrowAnyException();
    }

    // --- reading the cookie back ---------------------------------------------------------------

    @Test
    void readsTheTokenFromAMatchingCookie() {
        final var request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("refresh_token", "the-token") });

        assertThat(cookieWith("Strict").read(request)).contains("the-token");
    }

    @Test
    void readsNothingWhenTheBrowserSentNoCookiesAtAll() {
        final var request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        assertThat(cookieWith("Strict").read(request)).isEmpty();
    }

    /**
     * The branch at the end of {@code read} -- cookies present, none of them ours -- was never
     * executed. It is the ordinary state of any request from a browser that holds other cookies
     * for the origin, so "no refresh cookie" must not be confused with "no cookies".
     */
    @Test
    void readsNothingWhenOnlyUnrelatedCookiesArePresent() {
        final var request = mock(HttpServletRequest.class);
        when(request.getCookies())
                .thenReturn(new Cookie[] { new Cookie("session_hint", "x"), new Cookie("theme", "dark") });

        assertThat(cookieWith("Strict").read(request)).isEmpty();
    }

    /** An empty value is not a token; treating it as one would send a blank credential upstream. */
    @Test
    void readsNothingWhenTheCookieIsPresentButBlank() {
        final var request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("refresh_token", "") });

        assertThat(cookieWith("Strict").read(request)).isEmpty();
    }

    // --- the attributes the browser is asked to honour ----------------------------------------

    @Test
    void buildsAnHttpOnlyCookieCarryingTheConfiguredAttributes() {
        final var cookie = cookieWith("Strict").build("the-token");

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getValue()).isEqualTo("the-token");
    }

    /**
     * clear() must reproduce the attributes build() set. A browser treats a cookie with a
     * different path or SameSite as a different cookie, so a mismatch would leave the original in
     * place and make logout revocable only by waiting for the token to expire.
     */
    @Test
    void clearsWithTheSameAttributesItSetSoTheBrowserActuallyDropsIt() {
        final var component = cookieWith("Strict");
        final var set = component.build("the-token");
        final var cleared = component.clear();

        assertThat(cleared.getName()).isEqualTo(set.getName());
        assertThat(cleared.getPath()).isEqualTo(set.getPath());
        assertThat(cleared.getSameSite()).isEqualTo(set.getSameSite());
        assertThat(cleared.isHttpOnly()).isEqualTo(set.isHttpOnly());
        assertThat(cleared.isSecure()).isEqualTo(set.isSecure());
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge().isZero()).isTrue();
    }
}

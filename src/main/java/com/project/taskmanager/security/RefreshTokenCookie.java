package com.project.taskmanager.security;

import java.time.Duration;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds, reads and clears the httpOnly cookie the refresh token travels in.
 * <p>
 * The refresh token used to be returned in the response body and kept in {@code localStorage}, where
 * any script on the origin can read it. It is the credential worth stealing -- it is long-lived and
 * mints access tokens -- so an XSS that reached it turned a session-scoped problem into persistent
 * account takeover. In an httpOnly cookie it is not reachable from JavaScript at all.
 * <p>
 * {@code SameSite=Strict} is what removes the CSRF exposure the cookie would otherwise introduce:
 * the browser will not attach it to any cross-site request, so a request from another origin cannot
 * carry it. That is available here because the SPA and this API are same-origin --
 * {@code axiosSetup}'s {@code baseURL} is empty unless {@code VITE_API_URL} is set, so requests go
 * to {@code /api/...} relative to the page, proxied by Vite in development and served alongside the
 * SPA in production. It is also why this service has no CORS configuration at all.
 */
@Component
public class RefreshTokenCookie {

    @Getter
    @Value("${jwt.refreshCookie.name}")
    private String name;

    @Value("${jwt.refreshCookie.path}")
    private String path;

    @Value("${jwt.refreshCookie.sameSite}")
    private String sameSite;

    @Value("${jwt.refreshCookie.secure}")
    private boolean secure;

    /**
     * Matches the lifetime of the token itself. A cookie that outlived its token would leave the
     * browser sending a credential the server has already deleted, and one that expired first would
     * sign the user out early.
     */
    @Value("${jwt.refreshTokenExpiration}")
    private long refreshTokenExpiration;

    /** The cookie carrying {@code token}, ready to be set as a {@code Set-Cookie} header. */
    public ResponseCookie build(final String token) {
        return base(token).maxAge(Duration.ofMillis(refreshTokenExpiration)).build();
    }

    /**
     * An expired, empty cookie with the same name, path and attributes.
     * <p>
     * The attributes have to match the ones it was set with, or the browser treats it as a different
     * cookie and the original survives -- which on logout would leave the session revocable only by
     * waiting for the token to expire.
     */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /** The refresh token from the request's cookies, or empty when the browser sent none. */
    public Optional<String> read(final HttpServletRequest request) {
        final var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (final var cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Refuses to start when {@code SameSite=None} is configured, because this application disables
     * CSRF protection entirely and {@code SameSite} is the only thing standing in its place.
     * <p>
     * {@code SecurityConfig} calls {@code http.csrf(AbstractHttpConfigurer::disable)}. That is
     * correct while the refresh cookie is {@code Strict} or {@code Lax}: the browser will not
     * attach it to a cross-site request, so there is no ambient credential for a forged request to
     * ride on. Set it to {@code None} and the reasoning collapses -- the cookie travels cross-site
     * and nothing checks a token.
     * <p>
     * The danger is that this is a single injected string with no error attached to getting it
     * wrong, and the repository actively invites the mistake: the README documents a cross-origin
     * deployment via {@code VITE_API_URL}, in which a {@code Strict} cookie is never sent at all.
     * An operator hitting that wall sees one obvious knob. Turning it silently removes the last
     * CSRF defence, and nothing anywhere reports it.
     * <p>
     * Cross-site delivery is a legitimate thing to want. It just needs CSRF protection turned back
     * on and CORS configured with credentials -- not this value flipped on its own.
     */
    @PostConstruct
    public void rejectUnsafeSameSite() {
        if (sameSite != null && "none".equalsIgnoreCase(sameSite.trim())) {
            throw new IllegalStateException(
                    "jwt.refreshCookie.sameSite is None, which lets the refresh cookie travel on "
                            + "cross-site requests. This application disables CSRF protection and relies "
                            + "on SameSite instead, so None removes the only defence and leaves the API "
                            + "open to cross-site request forgery. Use Strict or Lax; if cross-site "
                            + "delivery is genuinely required, enable CSRF protection and configure CORS "
                            + "with credentials first.");
        }
    }

    private ResponseCookie.ResponseCookieBuilder base(final String value) {
        return ResponseCookie.from(name, value)
                // Not readable from JavaScript. This is the whole point of the change.
                .httpOnly(true).secure(secure).sameSite(sameSite).path(path);
    }

}

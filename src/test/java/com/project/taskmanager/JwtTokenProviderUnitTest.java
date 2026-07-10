package com.project.taskmanager;

import com.project.taskmanager.security.JwtTokenProvider;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderUnitTest {

    // Throwaway. Long enough for HS256, which requires at least 256 bits.
    private static final String SECRET = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String USERNAME = "username";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        setConfig(3_600_000, 86_400_000L);
    }

    private void setConfig(final int accessTokenExpiration, final long refreshTokenExpiration) {
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", refreshTokenExpiration);
    }

    @Test
    void shouldRoundTripTheUsernameThroughAnAccessToken() {
        final var token = tokenProvider.generateAccessToken(USERNAME);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsername(token)).isEqualTo(USERNAME);
    }

    @Test
    void shouldRejectAnExpiredToken() {
        setConfig(-1_000, 86_400_000L);
        final var expired = tokenProvider.generateAccessToken(USERNAME);

        assertThat(tokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    void shouldRejectATamperedToken() {
        final var token = tokenProvider.generateAccessToken(USERNAME);
        // Flip the last character of the signature.
        final var lastChar = token.charAt(token.length() - 1);
        final var tampered = token.substring(0, token.length() - 1) + (lastChar == 'A' ? 'B' : 'A');

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void shouldRejectATokenSignedWithAnotherSecret() {
        final var token = tokenProvider.generateAccessToken(USERNAME);

        final var otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "jwtSecret",
                "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100");
        ReflectionTestUtils.setField(otherProvider, "accessTokenExpiration", 3_600_000);
        ReflectionTestUtils.setField(otherProvider, "refreshTokenExpiration", 86_400_000L);

        assertThat(otherProvider.validateToken(token)).isFalse();
    }

    @Test
    void shouldRejectGarbage() {
        assertThat(tokenProvider.validateToken("not-a-jwt")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    /**
     * HS256 needs at least 256 bits of key. The deprecated signWith(SignatureAlgorithm,
     * String) overload accepted anything; Keys.hmacShaKeyFor refuses, so a too-short
     * secret now fails loudly instead of producing weakly signed tokens.
     */
    @Test
    void shouldRefuseToSignWithATooShortSecret() {
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", "short");

        assertThrows(WeakKeyException.class, () -> tokenProvider.generateAccessToken(USERNAME));
    }

    @Test
    void shouldBuildARefreshTokenCarryingUsernameAndFutureExpiry() {
        final var refreshToken = tokenProvider.generateRefreshToken(USERNAME);

        assertThat(refreshToken.getUsername()).isEqualTo(USERNAME);
        assertThat(refreshToken.getToken()).isNotBlank();
        assertThat(refreshToken.getExpiryDate()).isAfter(Instant.now());
        assertThat(refreshToken.isExpired()).isFalse();
        assertThat(tokenProvider.getUsername(refreshToken.getToken())).isEqualTo(USERNAME);
    }

    /**
     * A refresh token is a database key, so it must be unique. Built from only subject, iat and
     * exp — all second-resolution — two tokens minted for the same user within the same second
     * were byte-identical. That silently broke rotation (the "new" token equalled the old one)
     * and made two logins in the same second share a refresh token.
     */
    @Test
    void shouldMintADistinctRefreshTokenEveryTime() {
        final var first = tokenProvider.generateRefreshToken(USERNAME);
        final var second = tokenProvider.generateRefreshToken(USERNAME);

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
        assertThat(tokenProvider.getUsername(first.getToken())).isEqualTo(USERNAME);
        assertThat(tokenProvider.getUsername(second.getToken())).isEqualTo(USERNAME);
    }

    @Test
    void shouldResolveABearerToken() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");

        assertThat(tokenProvider.resolveToken(request)).isEqualTo("abc.def.ghi");
    }

    @Test
    void shouldNotResolveAHeaderWithoutTheBearerPrefix() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic abc");

        assertThat(tokenProvider.resolveToken(request)).isNull();
    }

    @Test
    void shouldNotResolveAMissingHeader() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(tokenProvider.resolveToken(request)).isNull();
    }
}

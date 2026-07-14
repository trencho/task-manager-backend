package com.project.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.repository.RefreshTokenRepository;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.service.impl.RefreshTokenServiceImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplUnitTest {

    private static final String USERNAME = "username";
    private static final String TOKEN = "refresh-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private static RefreshToken tokenExpiringAt(final Instant expiryDate) {
        return RefreshToken.builder()
                .token(TOKEN)
                .username(USERNAME)
                .expiryDate(expiryDate)
                .build();
    }

    @Test
    void shouldIssueAccessTokenForValidRefreshToken() {
        final var stored = tokenExpiringAt(Instant.now().plus(1, ChronoUnit.HOURS));
        final var rotated = RefreshToken.builder()
                .token("rotated-token")
                .username(USERNAME)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(refreshTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.generateAccessToken(USERNAME)).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(USERNAME)).thenReturn(rotated);
        when(refreshTokenRepository.save(rotated)).thenReturn(rotated);

        final var response = refreshTokenService.refreshAccessToken(TOKEN);

        assertEquals("new-access-token", response.accessToken());
        assertEquals("rotated-token", response.refreshToken());
    }

    /**
     * Rotation: the presented token is deleted, so a captured copy is good for a single use.
     * Without this a stolen refresh token stayed valid for its whole lifetime.
     */
    @Test
    void shouldRotateTheRefreshTokenAndDeleteTheOldOne() {
        final var stored = tokenExpiringAt(Instant.now().plus(1, ChronoUnit.HOURS));
        final var rotated = RefreshToken.builder()
                .token("rotated-token")
                .username(USERNAME)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(refreshTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.generateAccessToken(USERNAME)).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(USERNAME)).thenReturn(rotated);
        when(refreshTokenRepository.save(rotated)).thenReturn(rotated);

        final var response = refreshTokenService.refreshAccessToken(TOKEN);

        verify(refreshTokenRepository).delete(stored);
        verify(refreshTokenRepository).save(rotated);
        assertThat(response.refreshToken()).isNotEqualTo(TOKEN);
    }

    /**
     * An expired refresh token must not mint a new access token. Before this was fixed,
     * refreshAccessToken looked the token up and issued straight away: verifyExpiration
     * existed but no production path ever called it, so an expired refresh token kept
     * working indefinitely.
     */
    @Test
    void shouldRejectExpiredRefreshToken() {
        final var expired = tokenExpiringAt(Instant.now().minus(1, ChronoUnit.SECONDS));
        when(refreshTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(expired));

        final var thrown =
                assertThrows(IllegalArgumentException.class, () -> refreshTokenService.refreshAccessToken(TOKEN));

        assertThat(thrown.getMessage()).contains("expired");
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void shouldThrowWhenRefreshTokenIsUnknown() {
        when(refreshTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> refreshTokenService.refreshAccessToken(TOKEN));
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void shouldPersistGeneratedRefreshToken() {
        final var generated = tokenExpiringAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(jwtTokenProvider.generateRefreshToken(USERNAME)).thenReturn(generated);
        when(refreshTokenRepository.save(generated)).thenReturn(generated);

        assertEquals(generated, refreshTokenService.createRefreshToken(USERNAME));
        verify(refreshTokenRepository).save(generated);
    }

    @Test
    void shouldTreatTokenPastItsExpiryAsInvalid() {
        assertThat(refreshTokenService.isTokenValid(
                        tokenExpiringAt(Instant.now().plus(1, ChronoUnit.MINUTES))))
                .isTrue();
        assertThat(refreshTokenService.isTokenValid(
                        tokenExpiringAt(Instant.now().minus(1, ChronoUnit.MINUTES))))
                .isFalse();
    }

    @Test
    void verifyExpirationDeletesTheTokenItRejects() {
        final var expired = tokenExpiringAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThrows(IllegalArgumentException.class, () -> refreshTokenService.verifyExpiration(expired));
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void verifyExpirationPassesAValidTokenThrough() {
        final var valid = tokenExpiringAt(Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThat(refreshTokenService.verifyExpiration(valid)).contains(valid);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void shouldDelegateDeletions() {
        refreshTokenService.deleteByToken(TOKEN);
        refreshTokenService.deleteByUsername(USERNAME);

        verify(refreshTokenRepository).deleteByToken(TOKEN);
        verify(refreshTokenRepository).deleteByUsername(USERNAME);
    }
}

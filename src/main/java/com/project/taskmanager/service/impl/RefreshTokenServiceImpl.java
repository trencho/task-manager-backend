package com.project.taskmanager.service.impl;

import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.repository.RefreshTokenRepository;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.service.RefreshTokenService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(final String username) {
        final var refreshToken = jwtTokenProvider.generateRefreshToken(username);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Exchanges a refresh token for a new access token <em>and a new refresh token</em>, and
     * deletes the one presented. Rotation means a captured refresh token has a single use: once
     * the legitimate client redeems it, the copy is dead. Without it, a token stolen at any point
     * stayed usable for its whole lifetime.
     */
    @Override
    @Transactional
    public TokenResponseDTO refreshAccessToken(final String refreshToken) {
        final var storedToken = refreshTokenRepository
                .findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        // Rejects and deletes an expired token. Without this an expired refresh token
        // kept minting access tokens for as long as the row survived.
        verifyExpiration(storedToken);

        final var username = storedToken.getUsername();
        refreshTokenRepository.delete(storedToken);
        final var rotatedToken = refreshTokenRepository.save(jwtTokenProvider.generateRefreshToken(username));

        return new TokenResponseDTO(jwtTokenProvider.generateAccessToken(username), rotatedToken.getToken());
    }

    @Override
    public Optional<RefreshToken> findByToken(final String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public boolean isTokenValid(final RefreshToken refreshToken) {
        return !refreshToken.isExpired();
    }

    @Override
    public void deleteByToken(final String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Override
    public void deleteByUsername(final String username) {
        refreshTokenRepository.deleteByUsername(username);
    }

    @Override
    public Optional<RefreshToken> verifyExpiration(final RefreshToken token) {
        if (!isTokenValid(token)) {
            refreshTokenRepository.delete(token);
            throw new IllegalArgumentException("Refresh token expired. Please sign in again.");
        }
        return Optional.of(token);
    }
}

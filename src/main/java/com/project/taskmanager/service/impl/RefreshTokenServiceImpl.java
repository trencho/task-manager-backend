package com.project.taskmanager.service.impl;

import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.repository.RefreshTokenRepository;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    @Override
    public String refreshAccessToken(final String refreshToken) {
        final var storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        // Rejects and deletes an expired token. Without this an expired refresh token
        // kept minting access tokens for as long as the row survived.
        verifyExpiration(storedToken);

        return jwtTokenProvider.generateAccessToken(storedToken.getUsername());
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

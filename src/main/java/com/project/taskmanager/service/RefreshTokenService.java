package com.project.taskmanager.service;

import java.util.Optional;

import com.project.taskmanager.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(String username);

    /**
     * Rotates the supplied refresh token and mints a new access token.
     * <p>
     * Returns both halves because the caller does two different things with them: the access token
     * goes in the response body, the rotated refresh token goes in the Set-Cookie header. Only the
     * first is ever serialized.
     */
    TokenPair refreshAccessToken(String refreshToken);

    boolean isTokenValid(RefreshToken refreshToken);

    void deleteByToken(String token);

    void deleteByUsername(String userId);

    Optional<RefreshToken> verifyExpiration(RefreshToken token);
}

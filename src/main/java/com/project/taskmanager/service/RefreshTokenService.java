package com.project.taskmanager.service;

import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(String username);

    TokenResponseDTO refreshAccessToken(String refreshToken);

    Optional<RefreshToken> findByToken(String token);

    boolean isTokenValid(RefreshToken refreshToken);

    void deleteByToken(String token);

    void deleteByUsername(String userId);

    Optional<RefreshToken> verifyExpiration(RefreshToken token);

}

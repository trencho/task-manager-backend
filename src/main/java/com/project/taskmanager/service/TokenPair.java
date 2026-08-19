package com.project.taskmanager.service;

/**
 * Both halves of a freshly minted session, for server-side use only.
 * <p>
 * This is not a response body and must never become one: the refresh token reaches the browser as an
 * httpOnly cookie and nowhere else. The wire shape is
 * {@link com.project.taskmanager.dto.TokenResponseDTO}, which carries the access token alone.
 */
public record TokenPair(String accessToken, String refreshToken) {
}

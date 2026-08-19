package com.project.taskmanager.dto;

/**
 * What a successful sign-in or refresh returns: an access token, and nothing else.
 * <p>
 * The refresh token used to be here too. It is now delivered only as an httpOnly cookie, which no
 * script on the page can read -- so there is deliberately no field for it. Server-side code that
 * needs both halves uses {@link com.project.taskmanager.service.TokenPair}, which never reaches JSON.
 */
public record TokenResponseDTO(String accessToken) {
}

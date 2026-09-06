package com.project.taskmanager.exception;

/**
 * A refresh token that cannot be exchanged: unknown, already rotated away, or past its expiry.
 *
 * <p>A dedicated type rather than the bare {@code RuntimeException} the service used to throw,
 * because {@code /api/auth/refresh-token} is {@code permitAll} and the mapping to 401 is now
 * declared centrally. A catch-all on {@code RuntimeException} in an advice would answer 401 for
 * every unexpected fault in every controller, which is a status that tells the caller to sign in
 * again when the truth is that the server broke.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(final String message) {
        super(message);
    }
}

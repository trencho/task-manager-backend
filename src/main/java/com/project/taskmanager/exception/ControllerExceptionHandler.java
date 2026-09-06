package com.project.taskmanager.exception;

import java.util.List;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ControllerExceptionHandler {

    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<String>> handleValidationException(final MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();

        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Method-PARAMETER violations, the other half of the pair.
     *
     * <p>{@code MethodArgumentNotValidException} above covers a {@code @Valid @RequestBody}; a
     * {@code @Min}/{@code @Max} on a {@code @RequestParam} raises this instead. Only the body half
     * was handled, so once {@code @Validated} made the parameter constraints actually run, a
     * rejected value surfaced as an unmapped 500 rather than a 400. A guard that turns bad input
     * into a server error is only half a guard.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<List<String>> handleConstraintViolation(final ConstraintViolationException ex) {
        final List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage()).toList();

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<String> handleTaskNotFoundException(final TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /**
     * Registration refusing a username that is already taken. The body is a bare {@code String}
     * rather than the {@code List<String>} the two validation handlers above emit, which is the
     * shape {@code AuthController} answered with before this moved here. The client decodes both,
     * so changing it would be a contract break dressed as a tidy-up.
     *
     * <p>The only production path that raises this is {@code UserServiceImpl.registerUser}. It was
     * also the refresh path's expired-token signal until that gained
     * {@link InvalidRefreshTokenException}, which is what lets one status be declared here.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(final IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials() {
        // A fixed string. Naming which half was wrong tells an attacker which usernames exist.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
    }

    /**
     * {@code /api/auth/refresh-token} is {@code permitAll}, so this body reaches an anonymous
     * caller. Echoing the exception message handed them whatever failed inside: a Mongo error, an
     * NPE, or the bare "Refresh token not found". Log the cause, return a fixed string.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(final InvalidRefreshTokenException ex) {
        log.warn("Refresh token exchange failed", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_REFRESH_TOKEN);
    }
}

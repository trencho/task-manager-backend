package com.project.taskmanager;

import com.project.taskmanager.exception.ControllerExceptionHandler;
import com.project.taskmanager.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three mappings that moved out of {@code AuthController}. Every body here is asserted
 * literally, because the frontend decodes each of these shapes by hand: a rejected signup answers
 * a bare {@code String} while the two validation handlers answer a {@code List<String>}, and
 * "improving" that into one shape is a contract break wearing a tidy-up's clothes.
 */
class ControllerExceptionHandlerUnitTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void aRejectedRegistrationIsA400CarryingTheReason() {
        final var response = handler.handleIllegalArgument(new IllegalArgumentException("User already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // A bare String, not a List<String>. RegisterForm renders whichever it is given, and it
        // only has to cope with both because the server answers two shapes for one status.
        assertThat(response.getBody()).isEqualTo("User already exists");
    }

    @Test
    void aFailedLoginIsA401WithAFixedString() {
        final var response = handler.handleBadCredentials();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Invalid credentials");
    }

    @Test
    void aFailedLoginNeverNamesWhichHalfWasWrong() {
        // "Bad password for alice" would confirm alice exists. The handler takes no argument at
        // all, which is the structural version of this guarantee.
        final var response = handler.handleBadCredentials();

        assertThat(response.getBody()).doesNotContain("password").doesNotContain("username");
    }

    @Test
    void aRefusedRefreshIsA401WithAFixedString() {
        final var response = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("Token not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Invalid or expired refresh token");
    }

    /**
     * {@code /api/auth/refresh-token} is {@code permitAll}, so this body reaches an anonymous
     * caller. The exception message is whatever failed inside -- a Mongo error, an NPE, or the bare
     * "Refresh token not found" -- and none of it may survive into the response.
     */
    @Test
    void aRefusedRefreshLeaksNothingFromTheCause() {
        final var response = handler
                .handleInvalidRefreshToken(new InvalidRefreshTokenException("Mongo timed out on refresh_tokens"));

        assertThat(response.getBody()).isEqualTo("Invalid or expired refresh token").doesNotContain("Mongo")
                .doesNotContain("refresh_tokens");
    }

    /**
     * Not merely that the two 401s exist, but that they say different things -- a caller who
     * mistyped a password and a caller whose session expired need different next steps, and one
     * shared message would tell neither of them which they are.
     */
    @Test
    void theTwoUnauthorizedBodiesAreDistinguishable() {
        final var badCredentials = handler.handleBadCredentials().getBody();
        final var badRefresh = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("x")).getBody();

        assertThat(badCredentials).isNotEqualTo(badRefresh);
    }
}

package com.project.taskmanager;

import java.util.Optional;

import com.project.taskmanager.controller.AuthController;
import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.dto.UserLoginDTO;
import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.entity.User;
import com.project.taskmanager.exception.InvalidRefreshTokenException;
import com.project.taskmanager.mapper.UserMapper;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.security.RefreshTokenCookie;
import com.project.taskmanager.service.RefreshTokenService;
import com.project.taskmanager.service.TokenPair;
import com.project.taskmanager.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenCookie refreshTokenCookie;

    @InjectMocks
    private AuthController authController;

    @Test
    void shouldRegisterUserSuccessfully() {
        final var userRegistrationDTO = new UserRegistrationDTO("username", "email@example.com", "password");
        final var user = new User();
        user.setUsername("username");
        user.setEmail("email@example.com");
        user.setPassword("password");

        when(userMapper.toEntity(userRegistrationDTO)).thenReturn(user);
        doNothing().when(userService).registerUser(user);

        final var response = authController.register(userRegistrationDTO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User registered successfully!", response.getBody());
    }

    /**
     * The controller no longer maps this to a status; ControllerExceptionHandler does. What is
     * asserted here is that it PROPAGATES rather than being swallowed. The status and the body are
     * pinned by ControllerExceptionHandlerUnitTest and driven for real by
     * AuthControllerIntegrationTest.
     */
    @Test
    void shouldPropagateARejectedRegistration() {
        final var userRegistrationDTO = new UserRegistrationDTO("username", "email@example.com", "password");
        when(userMapper.toEntity(userRegistrationDTO)).thenThrow(new IllegalArgumentException("User already exists"));

        final var thrown = assertThrows(IllegalArgumentException.class,
                () -> authController.register(userRegistrationDTO));

        assertEquals("User already exists", thrown.getMessage());
    }

    @Test
    void shouldLoginSuccessfully() {
        final var userLoginDTO = new UserLoginDTO("username", "password");

        final var authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        final var refreshToken = mock(RefreshToken.class);
        when(tokenProvider.generateAccessToken(anyString())).thenReturn("mocked-jwt-token");
        when(refreshTokenService.createRefreshToken(anyString())).thenReturn(refreshToken);
        // The controller sets the refresh cookie on this path now. The mock returns null without a
        // stub, and ResponseCookie.toString() on null is what fails.
        when(refreshTokenCookie.build(any()))
                .thenReturn(ResponseCookie.from("task_manager_refresh_token", "cookie-value").build());

        final var response = authController.login(userLoginDTO);

        // The access token alone: the refresh token left the body in step 3 of the cookie migration.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new TokenResponseDTO("mocked-jwt-token"), response.getBody());
    }

    @Test
    void shouldPropagateBadCredentials() {
        final var userLoginDTO = new UserLoginDTO("username", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThrows(BadCredentialsException.class, () -> authController.login(userLoginDTO));

        // No token is minted for a caller who failed authentication.
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void shouldReturnNewAccessToken() {
        final var refreshToken = "valid-refresh-token";
        final var newAccessToken = "new-access-token";

        when(refreshTokenService.refreshAccessToken(refreshToken))
                .thenReturn(new TokenPair(newAccessToken, "rotated-refresh-token"));
        // The cookie is now the only source the controller reads, and the only place the rotated
        // token goes. The mock returns null without a stub, and ResponseCookie.toString() on null is
        // what fails.
        when(refreshTokenCookie.read(any())).thenReturn(Optional.of(refreshToken));
        when(refreshTokenCookie.build(any()))
                .thenReturn(ResponseCookie.from("task_manager_refresh_token", "cookie-value").build());

        final var response = authController.refreshToken(new MockHttpServletRequest());

        final var body = (TokenResponseDTO) response.getBody();
        assertEquals(newAccessToken, body.accessToken());
    }

    /**
     * The migration's body fallback is gone, so a request with no cookie is refused whatever it
     * carries -- the service is never even asked.
     */
    @Test
    void shouldReturnUnauthorizedWhenNoCookieIsPresent() {
        final var response = authController.refreshToken(new MockHttpServletRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(refreshTokenService, never()).refreshAccessToken(any());
    }

    /**
     * The internal message must never reach the caller. That guarantee moved to the advice, which
     * answers a fixed string and logs the cause -- ControllerExceptionHandlerUnitTest pins the
     * literal and asserts the message does not survive. What is left to assert here is that the
     * controller refuses to build a token response out of a failed exchange.
     */
    @Test
    void shouldNotAnswerWithATokenWhenTheExchangeFails() {
        final var refreshToken = "invalid-refresh-token";

        when(refreshTokenService.refreshAccessToken(refreshToken))
                .thenThrow(new InvalidRefreshTokenException("Refresh token not found"));
        when(refreshTokenCookie.read(any())).thenReturn(Optional.of(refreshToken));

        assertThrows(InvalidRefreshTokenException.class,
                () -> authController.refreshToken(new MockHttpServletRequest()));

        verify(refreshTokenCookie, never()).build(any());
    }
}

package com.project.taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.taskmanager.dto.RefreshTokenRequestDTO;
import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.dto.UserLoginDTO;
import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.entity.User;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.service.RefreshTokenService;
import com.project.taskmanager.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider tokenProvider;

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Possession of the refresh token is the authority to revoke it, so logout is permitted
     * without a valid access token — a client whose access token has already expired must
     * still be able to sign out.
     */
    @Test
    void shouldRevokeTheRefreshTokenOnLogout() throws Exception {
        final var request = new RefreshTokenRequestDTO("refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).deleteByToken("refresh-token");
    }

    /**
     * Logout is idempotent: signing out twice, or with a token the server has never seen,
     * is a success. Reporting 404 would let a caller probe which refresh tokens exist.
     */
    @Test
    void shouldTreatLogoutOfAnUnknownTokenAsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new RefreshTokenRequestDTO("never-issued"))))
                .andExpect(status().isNoContent());
    }

    /**
     * Unlike single-session logout, revoking every session is an authenticated action: the
     * caller must prove who they are, because no token is presented to establish authority.
     * `/api/auth/**` is permitAll, so this needs its own matcher ahead of that rule.
     */
    @Test
    @WithMockUser(username = "username")
    void shouldRevokeEverySessionForTheAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all")).andExpect(status().isNoContent());

        verify(refreshTokenService).deleteByUsername("username");
    }

    @Test
    void shouldRejectLogoutAllWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).deleteByUsername(any());
    }

    @Test
    void shouldRejectLogoutWithoutARefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(refreshTokenService, never()).deleteByToken(any());
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        final var userRegistrationDTO = new UserRegistrationDTO("username", "email@example.com", "password");

        doNothing().when(userService).registerUser(any(User.class));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userRegistrationDTO)))
                .andExpect(status().isOk())
                .andExpect(content().string("User registered successfully!"));

        // Drive the real UserMapper: assert it mapped every field of the DTO onto the entity
        // handed to the service. Mocking the mapper here left signup's DTO->entity mapping untested.
        final var mapped = ArgumentCaptor.forClass(User.class);
        verify(userService).registerUser(mapped.capture());
        assertEquals("username", mapped.getValue().getUsername());
        assertEquals("email@example.com", mapped.getValue().getEmail());
        assertEquals("password", mapped.getValue().getPassword());
    }

    @Test
    void shouldReturnBadRequestOnFailedRegistration() throws Exception {
        final var userRegistrationDTO = new UserRegistrationDTO("username", "email@example.com", "password");

        doThrow(new IllegalArgumentException("User already exists"))
                .when(userService)
                .registerUser(any(User.class));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userRegistrationDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User already exists"));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        final var userLoginDTO = new UserLoginDTO("username", "password");
        final var accessTokenString = "mocked-jwt-token";
        final var refreshTokenString = "mocked-refresh-token";

        final var authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        final var refreshToken = mock(RefreshToken.class);
        when(tokenProvider.generateAccessToken(anyString())).thenReturn(accessTokenString);
        when(refreshTokenService.createRefreshToken(anyString())).thenReturn(refreshToken);
        when(refreshToken.getToken()).thenReturn(refreshTokenString);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userLoginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(accessTokenString))
                .andExpect(jsonPath("$.refreshToken").value(refreshTokenString));
    }

    @Test
    void shouldFailLoginWithInvalidCredentials() throws Exception {
        final var userLoginDTO = new UserLoginDTO("username", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userLoginDTO)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid credentials"));
    }

    @Test
    void shouldReturnNewAccessTokenForValidRefreshToken() throws Exception {
        final var refreshToken = "valid-refresh-token";
        final var refreshTokenRequest = new RefreshTokenRequestDTO(refreshToken);
        final var newAccessToken = "new-access-token";

        when(refreshTokenService.refreshAccessToken(refreshToken))
                .thenReturn(new TokenResponseDTO(newAccessToken, "rotated-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(refreshTokenRequest)))
                .andExpect(status().isOk())
                // Rotation: the response carries a new refresh token too, and it is not the
                // one the caller presented.
                .andExpect(jsonPath("$.accessToken").value(newAccessToken))
                .andExpect(jsonPath("$.refreshToken").value("rotated-refresh-token"));
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken() throws Exception {
        final var refreshToken = "invalid-refresh-token";
        final var refreshTokenRequest = new RefreshTokenRequestDTO(refreshToken);

        when(refreshTokenService.refreshAccessToken(refreshToken))
                .thenThrow(new RuntimeException("Refresh token not found"));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(refreshTokenRequest)))
                .andExpect(status().isUnauthorized());
    }
}

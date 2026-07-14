package com.project.taskmanager.controller;

import com.project.taskmanager.dto.RefreshTokenRequestDTO;
import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.dto.UserLoginDTO;
import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.mapper.UserMapper;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.service.RefreshTokenService;
import com.project.taskmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/auth")
@RequiredArgsConstructor
@RestController
public class AuthController {

    private static final String USER_REGISTERED_SUCCESSFULLY = "User registered successfully!";
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<?> register(@Valid @RequestBody final UserRegistrationDTO userRegistrationDTO) {
        try {
            userService.registerUser(userMapper.toEntity(userRegistrationDTO));
            return ResponseEntity.ok(USER_REGISTERED_SUCCESSFULLY);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody final UserLoginDTO userLoginDTO) {
        try {
            final var username = userLoginDTO.username();
            final var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, userLoginDTO.password()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            final var accessToken = tokenProvider.generateAccessToken(username);
            final var refreshTokenEntity = refreshTokenService.createRefreshToken(username);

            return ResponseEntity.ok(new TokenResponseDTO(accessToken, refreshTokenEntity.getToken()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }
    }

    /**
     * Returns a new access token <em>and a rotated refresh token</em>. The presented refresh
     * token is invalidated, so a captured copy is good for at most one use. Clients must store
     * both values from the response.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(
            @Valid @RequestBody final RefreshTokenRequestDTO refreshTokenRequestDTO) {
        try {
            return ResponseEntity.ok(refreshTokenService.refreshAccessToken(refreshTokenRequestDTO.refreshToken()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    /**
     * Revokes the refresh token so it can no longer mint access tokens. Deliberately does not
     * require a valid access token: possession of the refresh token is the authority to revoke
     * it, and a client whose access token has already expired must still be able to sign out.
     * <p>
     * Idempotent — revoking a token the server never issued is a success. A 404 would let a
     * caller probe which refresh tokens exist. The outstanding access token remains valid until
     * it expires; that is inherent to stateless JWT, and is why the access token is short-lived.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody final RefreshTokenRequestDTO refreshTokenRequestDTO) {
        refreshTokenService.deleteByToken(refreshTokenRequestDTO.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes every refresh token belonging to the caller — "sign me out everywhere". Unlike
     * {@code /logout}, this requires authentication: no token is presented, so the caller must
     * prove who they are. Outstanding access tokens survive until they expire, as always.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal(expression = "username") final String username) {
        refreshTokenService.deleteByUsername(username);
        return ResponseEntity.noContent().build();
    }
}

package com.project.taskmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.project.taskmanager.dto.TokenResponseDTO;
import com.project.taskmanager.dto.UserLoginDTO;
import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.mapper.UserMapper;
import com.project.taskmanager.security.JwtTokenProvider;
import com.project.taskmanager.security.RefreshTokenCookie;
import com.project.taskmanager.service.RefreshTokenService;
import com.project.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final RefreshTokenCookie refreshTokenCookie;

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
            final var authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, userLoginDTO.password()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            final var accessToken = tokenProvider.generateAccessToken(username);
            final var refreshTokenEntity = refreshTokenService.createRefreshToken(username);

            // The refresh token travels in an httpOnly cookie and nowhere else. It is deliberately
            // absent from the body: anything the body carries is readable by any script on the
            // origin, which is the exposure this migration exists to remove.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.build(refreshTokenEntity.getToken()).toString())
                    .body(new TokenResponseDTO(accessToken));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }
    }

    /**
     * Returns a new access token and rotates the refresh cookie. The presented refresh token is
     * invalidated, so a captured copy is good for at most one use.
     * <p>
     * The cookie is the only accepted source. A refresh token submitted in the request body was
     * accepted during the migration so a browser on the previous bundle kept working; that path is
     * gone, and a caller that sends one now gets the same rejection as a caller that sends nothing.
     * There is nothing for the client to store either way.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(final HttpServletRequest request) {
        final var submitted = refreshTokenCookie.read(request).orElse(null);

        if (submitted == null || submitted.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No refresh token was supplied");
        }

        try {
            final var tokens = refreshTokenService.refreshAccessToken(submitted);
            // Rotation replaces the stored token, so the cookie has to be replaced with it --
            // otherwise the browser keeps sending one the server has just deleted.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.build(tokens.refreshToken()).toString())
                    .body(new TokenResponseDTO(tokens.accessToken()));
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
     * <p>
     * The cookie is the only accepted source, the migration's request-body fallback having been
     * removed. Nothing observable changes: the response was already 204 whether or not a token was
     * found.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(final HttpServletRequest request) {
        refreshTokenCookie.read(request).ifPresent(refreshTokenService::deleteByToken);

        // The cookie is httpOnly, so the browser cannot clear it -- only this response can.
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString()).build();
    }

    /**
     * Revokes every refresh token belonging to the caller — "sign me out everywhere". Unlike
     * {@code /logout}, this requires authentication: no token is presented, so the caller must
     * prove who they are. Outstanding access tokens survive until they expire, as always.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal(expression = "username") final String username) {
        refreshTokenService.deleteByUsername(username);
        // This browser's cookie goes too. Every token was revoked, so leaving it set would have the
        // browser sending a credential the server has already deleted on every refresh attempt.
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString()).build();
    }
}

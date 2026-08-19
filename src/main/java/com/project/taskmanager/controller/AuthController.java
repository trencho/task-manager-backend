package com.project.taskmanager.controller;

import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.project.taskmanager.dto.RefreshTokenRequestDTO;
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

            // The refresh token now travels in an httpOnly cookie, where no script can read it.
            //
            // It is STILL returned in the body as well, deliberately and temporarily. The SPA
            // deploys separately from this service, so a release that did only one side would break
            // sign-in for whichever shipped first: a browser on the old bundle reads the body, one
            // on the new bundle reads the cookie. Serving both means either order works. The body
            // field is removed once the frontend no longer reads it.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.build(refreshTokenEntity.getToken()).toString())
                    .body(new TokenResponseDTO(accessToken, refreshTokenEntity.getToken()));
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
            @RequestBody(required = false) final RefreshTokenRequestDTO refreshTokenRequestDTO,
            final HttpServletRequest request) {
        // Cookie first, body second. The body is still accepted so a browser running the previous
        // bundle keeps working while the two deploys catch up; it goes away in the final step.
        final var submitted = refreshTokenCookie.read(request)
                .orElseGet(() -> refreshTokenRequestDTO != null ? refreshTokenRequestDTO.refreshToken() : null);

        if (submitted == null || submitted.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No refresh token was supplied");
        }

        try {
            final var tokens = refreshTokenService.refreshAccessToken(submitted);
            // Rotation replaces the stored token, so the cookie has to be replaced with it --
            // otherwise the browser keeps sending one the server has just deleted.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.build(tokens.refreshToken()).toString())
                    .body(tokens);
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
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) final RefreshTokenRequestDTO refreshTokenRequestDTO,
            final HttpServletRequest request) {
        refreshTokenCookie.read(request)
                .or(() -> Optional
                        .ofNullable(refreshTokenRequestDTO != null ? refreshTokenRequestDTO.refreshToken() : null))
                .ifPresent(refreshTokenService::deleteByToken);

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

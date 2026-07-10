package com.project.taskmanager.security;

import com.project.taskmanager.entity.RefreshToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.accessTokenExpiration}")
    private int accessTokenExpiration;

    @Value("${jwt.refreshTokenExpiration}")
    private long refreshTokenExpiration;

    /**
     * Keys.hmacShaKeyFor rejects a secret shorter than the 256 bits HS256 requires. The
     * deprecated signWith(SignatureAlgorithm, String) overload accepted anything and
     * silently base64-decoded it.
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(final String username) {
        final var now = new Date();
        return buildToken(username, now, new Date(now.getTime() + accessTokenExpiration));
    }

    public RefreshToken generateRefreshToken(final String username) {
        final var now = new Date();
        // One expiry instant for both the signed claim and the persisted row: deriving
        // them from separate clock reads let them disagree by a millisecond.
        final var expiration = new Date(now.getTime() + refreshTokenExpiration);

        return RefreshToken.builder()
                .token(buildToken(username, now, expiration))
                .username(username)
                .expiryDate(expiration.toInstant())
                .build();
    }

    private String buildToken(final String username, final Date issuedAt, final Date expiration) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getUsername(final String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(final String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // An invalid token is an ordinary event on a public endpoint, not an
            // application error. At ERROR, any caller could flood the log.
            log.debug("Rejected JWT: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String resolveToken(final HttpServletRequest request) {
        final var bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

}

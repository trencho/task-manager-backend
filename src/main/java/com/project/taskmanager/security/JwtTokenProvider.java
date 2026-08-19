package com.project.taskmanager.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.SecretKey;

import com.project.taskmanager.entity.RefreshToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * SHA-256 digests of signing secrets that are public.
     * <p>
     * Both were committed to {@code src/main/resources/application.yml} in this repository's
     * history, which is public: a real 64-character key across six reachable commits (09d72a6,
     * b0abc2e, ccd4216, 175f43d, 06c5beb, a5c94dc) and, before it, the placeholder
     * {@code your_jwt_secret_key}. Deleting them from the config did not unpublish them --
     * rewriting history would not either, since pre-rewrite commits stay fetchable by SHA until
     * GitHub-side garbage collection.
     * <p>
     * The signing key is the whole of authentication here: anyone holding it mints a token for any
     * username, and every ownership check downstream trusts {@code @AuthenticationPrincipal}, which
     * trusts the signature. So booting with one of these is a total authentication bypass, and it
     * would be silent -- the application would start and serve traffic exactly as normal.
     * <p>
     * Digests rather than the values, so this guard does not republish what it exists to reject.
     */
    private static final Set<String> COMPROMISED_SECRET_DIGESTS = Set.of(
            "6a58da7939a535990e307aa880d49c426bc4f1e1e768a08705ed568cd4076e54",
            "29c1c075ba2ac0a3f16f4ca9486343758ad6e5af52c72fca11943317e3c51c71");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.accessTokenExpiration}")
    private int accessTokenExpiration;

    @Value("${jwt.refreshTokenExpiration}")
    private long refreshTokenExpiration;

    /**
     * Refuses to start when {@code jwt.secret} is one of the secrets published in this repository's
     * git history.
     * <p>
     * Rotation is an operational act, and an operational act that is merely written down somewhere
     * eventually does not happen. Without this, a deployment that never rotated looks identical to
     * one that did -- it boots, it serves, and every token it accepts is forgeable. Failing at
     * startup converts that into something impossible to miss.
     */
    @PostConstruct
    public void rejectCompromisedSecret() {
        if (COMPROMISED_SECRET_DIGESTS.contains(sha256Hex(jwtSecret))) {
            throw new IllegalStateException(
                    "jwt.secret is a value that was published in this repository's git history and "
                            + "must never be used. Anyone can read it and forge a token for any user. "
                            + "Generate a new secret, set JWT_SECRET, and invalidate outstanding "
                            + "refresh tokens, which were issued under the old key.");
        }
    }

    private static String sha256Hex(final String value) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            // Every JVM is required to provide SHA-256, so this cannot happen -- but failing closed
            // is the only safe reading if it somehow did.
            throw new IllegalStateException("SHA-256 is unavailable, so jwt.secret cannot be checked", e);
        }
    }

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
        return buildToken(username, now, new Date(now.getTime() + accessTokenExpiration), null);
    }

    public RefreshToken generateRefreshToken(final String username) {
        final var now = new Date();
        // One expiry instant for both the signed claim and the persisted row: deriving
        // them from separate clock reads let them disagree by a millisecond.
        final var expiration = new Date(now.getTime() + refreshTokenExpiration);

        // A refresh token is a database key, so it must be unique. `iat` and `exp` have
        // second resolution, so without a random `jti` two tokens minted for the same user
        // within the same second are byte-identical — which silently broke rotation (the
        // "new" token equalled the old one) and made two logins in one second collide.
        final var tokenId = UUID.randomUUID().toString();

        return RefreshToken.builder().token(buildToken(username, now, expiration, tokenId)).username(username)
                .expiryDate(expiration.toInstant()).build();
    }

    private String buildToken(final String username, final Date issuedAt, final Date expiration, final String tokenId) {
        final var builder = Jwts.builder().subject(username).issuedAt(issuedAt).expiration(expiration);

        if (tokenId != null) {
            builder.id(tokenId);
        }

        return builder.signWith(signingKey(), Jwts.SIG.HS256).compact();
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
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    public String resolveToken(final HttpServletRequest request) {
        final var bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

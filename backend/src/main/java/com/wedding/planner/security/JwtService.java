package com.wedding.planner.security;

import com.wedding.planner.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 JWT access tokens.
 *
 * <p>The token subject is the user's email; the persistent user id is carried in the {@code uid}
 * claim so it can be surfaced without a database round-trip when convenient.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
        this.issuer = properties.issuer();
    }

    /** Issues a signed token for the given principal. */
    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** @return the subject (email) if the token is well-formed and unexpired, else empty-throwing. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /**
     * @return {@code true} when the signature verifies, the issuer matches and the token has not
     * expired; {@code false} for any malformed, tampered or expired token.
     */
    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

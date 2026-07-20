package com.wedding.planner.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedding.planner.config.JwtProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Pure unit tests for token issuing and validation (no Spring context).
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-long-enough-123456";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, 3_600_000L, "wedding-planner"));

    private AppUserPrincipal principal(UUID id, String email) {
        return new AppUserPrincipal(id, email, "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_PLANNER")));
    }

    @Test
    void generatesTokenThatValidatesAndCarriesSubject() {
        UUID id = UUID.randomUUID();
        String token = jwtService.generateToken(principal(id, "planner@wedding.test"));

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("planner@wedding.test");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(principal(UUID.randomUUID(), "a@wedding.test"));
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "bb" : "aa");

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService(
                new JwtProperties("a-totally-different-secret-key-of-sufficient-length-1", 3_600_000L,
                        "wedding-planner"));
        String foreignToken = other.generateToken(principal(UUID.randomUUID(), "x@wedding.test"));

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLived = new JwtService(
                new JwtProperties(SECRET, -1_000L, "wedding-planner"));
        String expired = shortLived.generateToken(principal(UUID.randomUUID(), "y@wedding.test"));

        assertThat(jwtService.isTokenValid(expired)).isFalse();
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        JwtService foreignIssuer = new JwtService(
                new JwtProperties(SECRET, 3_600_000L, "some-other-issuer"));
        String token = foreignIssuer.generateToken(principal(UUID.randomUUID(), "z@wedding.test"));

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void rejectsGarbageInput() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}

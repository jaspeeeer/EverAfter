package com.wedding.planner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code app.jwt.*} settings.
 *
 * @param secret       HS256 signing secret (>= 32 bytes)
 * @param expirationMs access-token lifetime in milliseconds
 * @param issuer       the {@code iss} claim written into issued tokens
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs, String issuer) {
}

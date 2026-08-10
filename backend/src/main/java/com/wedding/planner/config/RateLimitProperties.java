package com.wedding.planner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Strongly-typed binding for the {@code app.rate-limit.*} settings. Two independently configured
 * buckets: the unauthenticated public surface ({@code /api/public/**}, token-guessing is
 * infeasible but the endpoint is still free reconnaissance/DoS amplification) and the auth
 * endpoints ({@code /api/auth/register}, {@code /api/auth/login} — an account-enumeration oracle
 * and a BCrypt CPU amplifier respectively).
 *
 * @param enabled            master switch; disabled in the test suites so seeding many
 *                           users/projects per run doesn't self-throttle
 * @param trustForwardedFor  whether to key on {@code X-Forwarded-For} instead of the socket
 *                           address. Defaults to {@code false} — that header is
 *                           client-spoofable unless a trusted reverse proxy strips/overwrites it,
 *                           and this app has no such proxy configured yet.
 * @param publicApi          bucket for {@code /api/public/**}
 * @param auth               bucket for {@code /api/auth/register} and {@code /api/auth/login}
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean trustForwardedFor,
        @NestedConfigurationProperty Bucket publicApi,
        @NestedConfigurationProperty Bucket auth) {

    /** A fixed request budget over a rolling window. */
    public record Bucket(int limit, long windowMs) {
    }
}

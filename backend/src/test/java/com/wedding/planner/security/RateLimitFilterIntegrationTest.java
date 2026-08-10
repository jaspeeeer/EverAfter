package com.wedding.planner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Deliberately does NOT extend {@link com.wedding.planner.AbstractIntegrationTest} — that base
 * class disables rate limiting (its other ~120 tests register/login many times per run and would
 * self-throttle). Re-enabling it here with tight, test-only limits needs its own Spring context
 * (a distinct property set is a distinct context-cache key), so it gets its own Postgres
 * container, matching the existing two-containers-per-build pattern
 * ({@code AbstractIntegrationTest} / {@code AbstractPostgresContainerTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterIntegrationTest {

    private static final int LIMIT = 3;

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wedding")
                    .withUsername("wedding")
                    .withPassword("wedding");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.rate-limit.enabled", () -> true);
        registry.add("app.rate-limit.public-api.limit", () -> LIMIT);
        registry.add("app.rate-limit.public-api.window-ms", () -> 60_000);
        registry.add("app.rate-limit.auth.limit", () -> LIMIT);
        registry.add("app.rate-limit.auth.window-ms", () -> 60_000);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicRsvpEndpointThrottlesPerIpAfterTheLimit() throws Exception {
        String path = "/api/public/rsvp/" + UUID.randomUUID();

        for (int i = 0; i < LIMIT; i++) {
            // The token is unknown, so every request 404s — the limiter runs before the guest
            // lookup and counts it regardless of the eventual outcome.
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }

        mockMvc.perform(get(path))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void loginEndpointThrottlesPerIpAfterTheLimit() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "nobody@wedding.test", "password", "wrong-password"));

        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":429");
    }
}

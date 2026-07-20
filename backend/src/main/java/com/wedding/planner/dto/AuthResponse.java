package com.wedding.planner.dto;

import java.util.List;
import java.util.UUID;

/**
 * Returned by register/login. {@code tokenType} is always {@code "Bearer"}.
 */
public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        String email,
        List<String> roles) {

    public static AuthResponse bearer(String token, UUID userId, String email, List<String> roles) {
        return new AuthResponse(token, "Bearer", userId, email, roles);
    }
}

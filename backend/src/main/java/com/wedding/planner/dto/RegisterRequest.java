package com.wedding.planner.dto;

import com.wedding.planner.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Self-service registration. Only {@code ROLE_PLANNER} or {@code ROLE_USER} may be requested;
 * admin accounts are provisioned out of band (see {@code DataInitializer}).
 *
 * @param inviteToken optional couple-invitation token; when present the new account (which must
 *                    be {@code ROLE_USER}) becomes the owning couple of the invited project.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull RoleName role,
        UUID inviteToken) {

    /** Convenience constructor for the common non-invited case (keeps existing tests terse). */
    public RegisterRequest(String email, String password, String firstName, String lastName,
                           RoleName role) {
        this(email, password, firstName, lastName, role, null);
    }
}

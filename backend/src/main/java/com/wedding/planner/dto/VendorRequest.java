package com.wedding.planner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record VendorRequest(
        @NotBlank String name,
        @NotNull UUID categoryId,
        @Email String contactEmail,
        String phone,
        boolean booked,
        @PositiveOrZero BigDecimal agreedPrice,
        /** Set to nest this vendor as an item under a package (a top-level vendor). */
        UUID parentId) {
}

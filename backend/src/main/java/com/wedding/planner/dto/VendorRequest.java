package com.wedding.planner.dto;

import com.wedding.planner.domain.VendorCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VendorRequest(
        @NotBlank String name,
        @NotNull VendorCategory category,
        @Email String contactEmail,
        String phone,
        boolean booked) {
}

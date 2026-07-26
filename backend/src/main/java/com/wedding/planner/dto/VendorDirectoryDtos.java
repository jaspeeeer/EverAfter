package com.wedding.planner.dto;

import com.wedding.planner.domain.VendorDirectoryEntry;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** DTOs for the global, admin-curated vendor directory. */
public final class VendorDirectoryDtos {

    private VendorDirectoryDtos() {
    }

    public record VendorDirectoryRequest(
            @NotBlank String name,
            @NotNull UUID categoryId,
            @Email String contactEmail,
            String phone,
            @PositiveOrZero BigDecimal typicalPrice,
            String notes,
            Boolean active) {
    }

    public record VendorDirectoryResponse(
            UUID id,
            String name,
            UUID categoryId,
            String categoryName,
            String contactEmail,
            String phone,
            BigDecimal typicalPrice,
            String notes,
            boolean active) {

        public static VendorDirectoryResponse from(VendorDirectoryEntry entry) {
            return new VendorDirectoryResponse(
                    entry.getId(),
                    entry.getName(),
                    entry.getCategory().getId(),
                    entry.getCategory().getName(),
                    entry.getContactEmail(),
                    entry.getPhone(),
                    entry.getTypicalPrice(),
                    entry.getNotes(),
                    entry.isActive());
        }
    }

    /** Body for adding a directory entry into a project as a new vendor. */
    public record AddFromDirectoryRequest(@NotNull UUID directoryId) {
    }
}

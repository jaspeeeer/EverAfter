package com.wedding.planner.dto;

import com.wedding.planner.domain.VendorCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTOs for the admin-managed vendor category lookup. */
public final class VendorCategoryDtos {

    private VendorCategoryDtos() {
    }

    public record VendorCategoryResponse(UUID id, String name, String slug, boolean active) {

        public static VendorCategoryResponse from(VendorCategory category) {
            return new VendorCategoryResponse(
                    category.getId(), category.getName(), category.getSlug(), category.isActive());
        }
    }

    /** Create only needs a name (slug is derived). */
    public record CreateVendorCategoryRequest(@NotBlank @Size(max = 60) String name) {
    }

    /** Update can rename and/or (de)activate. */
    public record UpdateVendorCategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Boolean active) {
    }
}

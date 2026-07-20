package com.wedding.planner.dto;

import com.wedding.planner.domain.ChecklistTemplate;
import com.wedding.planner.domain.ChecklistTemplateItem;
import com.wedding.planner.domain.VendorCategory;
import com.wedding.planner.domain.VendorTemplate;
import com.wedding.planner.domain.VendorTemplateItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** DTOs for admin-managed checklist/vendor templates. */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    // --- Checklist templates ---

    public record ChecklistItemRequest(
            @NotBlank String title,
            String description,
            @Min(0) Integer daysBeforeWedding) {
    }

    public record ChecklistTemplateRequest(
            @NotBlank String name,
            String description,
            @NotEmpty List<@Valid ChecklistItemRequest> items) {
    }

    public record ChecklistItemResponse(
            String title,
            String description,
            Integer daysBeforeWedding) {

        static ChecklistItemResponse from(ChecklistTemplateItem item) {
            return new ChecklistItemResponse(
                    item.getTitle(), item.getDescription(), item.getDaysBeforeWedding());
        }
    }

    public record ChecklistTemplateResponse(
            UUID id,
            String name,
            String description,
            List<ChecklistItemResponse> items) {

        public static ChecklistTemplateResponse from(ChecklistTemplate template) {
            return new ChecklistTemplateResponse(
                    template.getId(),
                    template.getName(),
                    template.getDescription(),
                    template.getItems().stream().map(ChecklistItemResponse::from).toList());
        }
    }

    // --- Vendor templates ---

    public record VendorItemRequest(
            @NotBlank String name,
            @NotNull VendorCategory category) {
    }

    public record VendorTemplateRequest(
            @NotBlank String name,
            String description,
            @NotEmpty List<@Valid VendorItemRequest> items) {
    }

    public record VendorItemResponse(String name, VendorCategory category) {

        static VendorItemResponse from(VendorTemplateItem item) {
            return new VendorItemResponse(item.getName(), item.getCategory());
        }
    }

    public record VendorTemplateResponse(
            UUID id,
            String name,
            String description,
            List<VendorItemResponse> items) {

        public static VendorTemplateResponse from(VendorTemplate template) {
            return new VendorTemplateResponse(
                    template.getId(),
                    template.getName(),
                    template.getDescription(),
                    template.getItems().stream().map(VendorItemResponse::from).toList());
        }
    }

    // --- Applying ---

    public record ApplyTemplateRequest(@NotNull UUID templateId) {
    }
}

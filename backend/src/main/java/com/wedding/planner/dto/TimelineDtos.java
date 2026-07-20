package com.wedding.planner.dto;

import com.wedding.planner.domain.TimelineEvent;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** DTOs for the wedding-day timeline. */
public final class TimelineDtos {

    private TimelineDtos() {
    }

    public record TimelineEventRequest(
            @NotBlank String title,
            String description,
            String location,
            @NotNull LocalTime startTime,
            LocalTime endTime,
            List<UUID> vendorIds) {
    }

    /** Supplier summary shown when a time slot is clicked. */
    public record EventVendorResponse(
            UUID id,
            String name,
            VendorCategory category,
            boolean booked,
            String contactEmail,
            String phone) {

        static EventVendorResponse from(Vendor vendor) {
            return new EventVendorResponse(
                    vendor.getId(),
                    vendor.getName(),
                    vendor.getCategory(),
                    vendor.isBooked(),
                    vendor.getContactEmail(),
                    vendor.getPhone());
        }
    }

    public record TimelineEventResponse(
            UUID id,
            String title,
            String description,
            String location,
            LocalTime startTime,
            LocalTime endTime,
            List<EventVendorResponse> vendors,
            UUID projectId) {

        public static TimelineEventResponse from(TimelineEvent event) {
            return new TimelineEventResponse(
                    event.getId(),
                    event.getTitle(),
                    event.getDescription(),
                    event.getLocation(),
                    event.getStartTime(),
                    event.getEndTime(),
                    event.getVendors().stream()
                            .map(EventVendorResponse::from)
                            .sorted(Comparator.comparing(EventVendorResponse::name))
                            .toList(),
                    event.getProject().getId());
        }
    }
}

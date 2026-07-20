package com.wedding.planner.dto;

import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorCategory;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String name,
        VendorCategory category,
        String contactEmail,
        String phone,
        boolean booked,
        UUID projectId) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getCategory(),
                vendor.getContactEmail(),
                vendor.getPhone(),
                vendor.isBooked(),
                vendor.getProject().getId());
    }
}

package com.wedding.planner.dto;

import com.wedding.planner.domain.Vendor;
import java.math.BigDecimal;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String name,
        UUID categoryId,
        String categoryName,
        String contactEmail,
        String phone,
        boolean booked,
        BigDecimal agreedPrice,
        BigDecimal amountPaid,
        UUID directoryId,
        UUID parentId,
        UUID projectId) {

    public static VendorResponse from(Vendor vendor) {
        return from(vendor, BigDecimal.ZERO);
    }

    public static VendorResponse from(Vendor vendor, BigDecimal amountPaid) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getCategory().getId(),
                vendor.getCategory().getName(),
                vendor.getContactEmail(),
                vendor.getPhone(),
                vendor.isBooked(),
                vendor.getAgreedPrice(),
                amountPaid,
                vendor.getDirectoryEntry() != null ? vendor.getDirectoryEntry().getId() : null,
                vendor.getParent() != null ? vendor.getParent().getId() : null,
                vendor.getProject().getId());
    }
}

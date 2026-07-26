package com.wedding.planner.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTOs for the admin vendor reports. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record VendorsByCategoryRow(
            UUID categoryId,
            String categoryName,
            long vendorCount,
            long bookedCount,
            BigDecimal totalAgreedValue) {
    }

    public record InDemandVendorRow(
            String vendorName,
            String categoryName,
            long usageCount,
            long bookedCount,
            BigDecimal totalAgreedValue,
            boolean fromDirectory) {
    }

    public record BookingConversionRow(
            String categoryName,
            long considered,
            long booked,
            double bookedRate) {
    }

    public record BookingConversionReport(
            List<BookingConversionRow> categories,
            long totalConsidered,
            long totalBooked,
            double overallRate) {
    }
}

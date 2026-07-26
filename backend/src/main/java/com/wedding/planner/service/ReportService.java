package com.wedding.planner.service;

import com.wedding.planner.dto.ReportDtos.BookingConversionReport;
import com.wedding.planner.dto.ReportDtos.BookingConversionRow;
import com.wedding.planner.dto.ReportDtos.InDemandVendorRow;
import com.wedding.planner.dto.ReportDtos.VendorsByCategoryRow;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-project admin reports over the vendor data. All queries are group-by aggregations on
 * {@link VendorRepository} (same shape as {@code UserRepository.countUsersByRole}); the rows are
 * mapped from {@code Object[]} here.
 */
@Service
public class ReportService {

    private final VendorRepository vendorRepository;

    public ReportService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorsByCategoryRow> vendorsByCategory() {
        return vendorRepository.vendorCountsByCategory().stream()
                .map(r -> new VendorsByCategoryRow(
                        (UUID) r[0],
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue(),
                        (BigDecimal) r[4]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InDemandVendorRow> inDemandVendors(LocalDate from, LocalDate to, UUID categoryId) {
        return vendorRepository.inDemandVendors(
                        from != null, from, to != null, to, categoryId != null, categoryId).stream()
                .map(r -> new InDemandVendorRow(
                        (String) r[0],
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue(),
                        (BigDecimal) r[4],
                        (Boolean) r[5]))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingConversionReport bookingConversion() {
        List<BookingConversionRow> rows = vendorRepository.bookingConversionByCategory().stream()
                .map(r -> {
                    long considered = ((Number) r[1]).longValue();
                    long booked = ((Number) r[2]).longValue();
                    return new BookingConversionRow(
                            (String) r[0], considered, booked, rate(booked, considered));
                })
                .toList();
        long totalConsidered = rows.stream().mapToLong(BookingConversionRow::considered).sum();
        long totalBooked = rows.stream().mapToLong(BookingConversionRow::booked).sum();
        return new BookingConversionReport(
                rows, totalConsidered, totalBooked, rate(totalBooked, totalConsidered));
    }

    private static double rate(long booked, long considered) {
        return considered == 0 ? 0.0 : Math.round((booked * 10000.0) / considered) / 100.0;
    }
}

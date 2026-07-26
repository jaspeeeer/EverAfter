package com.wedding.planner.web;

import com.wedding.planner.dto.ReportDtos.BookingConversionReport;
import com.wedding.planner.dto.ReportDtos.InDemandVendorRow;
import com.wedding.planner.dto.ReportDtos.VendorsByCategoryRow;
import com.wedding.planner.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cross-project admin vendor reports. CSV is built client-side from these JSON payloads. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/vendors-by-category")
    public List<VendorsByCategoryRow> vendorsByCategory() {
        return reportService.vendorsByCategory();
    }

    @GetMapping("/in-demand-vendors")
    public List<InDemandVendorRow> inDemandVendors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId) {
        return reportService.inDemandVendors(from, to, categoryId);
    }

    @GetMapping("/booking-conversion")
    public BookingConversionReport bookingConversion() {
        return reportService.bookingConversion();
    }
}

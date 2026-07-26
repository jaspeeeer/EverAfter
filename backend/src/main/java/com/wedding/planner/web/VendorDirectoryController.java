package com.wedding.planner.web;

import com.wedding.planner.dto.VendorDirectoryDtos.VendorDirectoryResponse;
import com.wedding.planner.service.VendorDirectoryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browse the active vendor directory. Planners (and admins) use this to add directory vendors to
 * a project; full admin management lives at {@code /api/admin/vendor-directory}.
 */
@RestController
@RequestMapping("/api/vendor-directory")
public class VendorDirectoryController {

    private final VendorDirectoryService vendorDirectoryService;

    public VendorDirectoryController(VendorDirectoryService vendorDirectoryService) {
        this.vendorDirectoryService = vendorDirectoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER')")
    public List<VendorDirectoryResponse> list() {
        return vendorDirectoryService.listActive();
    }
}

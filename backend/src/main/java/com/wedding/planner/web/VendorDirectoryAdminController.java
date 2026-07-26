package com.wedding.planner.web;

import com.wedding.planner.dto.VendorDirectoryDtos.VendorDirectoryRequest;
import com.wedding.planner.dto.VendorDirectoryDtos.VendorDirectoryResponse;
import com.wedding.planner.service.VendorDirectoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD for the global vendor directory (full list incl. inactive). */
@RestController
@RequestMapping("/api/admin/vendor-directory")
@PreAuthorize("hasRole('ADMIN')")
public class VendorDirectoryAdminController {

    private final VendorDirectoryService vendorDirectoryService;

    public VendorDirectoryAdminController(VendorDirectoryService vendorDirectoryService) {
        this.vendorDirectoryService = vendorDirectoryService;
    }

    @GetMapping
    public List<VendorDirectoryResponse> list() {
        return vendorDirectoryService.listAll();
    }

    @PostMapping
    public ResponseEntity<VendorDirectoryResponse> create(
            @Valid @RequestBody VendorDirectoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vendorDirectoryService.create(request));
    }

    @PutMapping("/{id}")
    public VendorDirectoryResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody VendorDirectoryRequest request) {
        return vendorDirectoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vendorDirectoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.wedding.planner.web;

import com.wedding.planner.dto.VendorCategoryDtos.CreateVendorCategoryRequest;
import com.wedding.planner.dto.VendorCategoryDtos.UpdateVendorCategoryRequest;
import com.wedding.planner.dto.VendorCategoryDtos.VendorCategoryResponse;
import com.wedding.planner.service.VendorCategoryService;
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

/** Admin management of the vendor category lookup (full list incl. inactive). */
@RestController
@RequestMapping("/api/admin/vendor-categories")
@PreAuthorize("hasRole('ADMIN')")
public class VendorCategoryAdminController {

    private final VendorCategoryService vendorCategoryService;

    public VendorCategoryAdminController(VendorCategoryService vendorCategoryService) {
        this.vendorCategoryService = vendorCategoryService;
    }

    @GetMapping
    public List<VendorCategoryResponse> list() {
        return vendorCategoryService.listAll();
    }

    @PostMapping
    public ResponseEntity<VendorCategoryResponse> create(
            @Valid @RequestBody CreateVendorCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vendorCategoryService.create(request));
    }

    @PutMapping("/{id}")
    public VendorCategoryResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateVendorCategoryRequest request) {
        return vendorCategoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vendorCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

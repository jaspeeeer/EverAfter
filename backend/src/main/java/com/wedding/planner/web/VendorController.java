package com.wedding.planner.web;

import com.wedding.planner.dto.TemplateDtos.ApplyTemplateRequest;
import com.wedding.planner.dto.VendorDirectoryDtos.AddFromDirectoryRequest;
import com.wedding.planner.dto.VendorPaymentDtos.VendorPaymentRequest;
import com.wedding.planner.dto.VendorPaymentDtos.VendorPaymentResponse;
import com.wedding.planner.dto.VendorRequest;
import com.wedding.planner.dto.VendorResponse;
import com.wedding.planner.service.TemplateService;
import com.wedding.planner.service.VendorService;
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

/**
 * Vendors nested under a project. Access is gated on the owning {@code projectId}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/vendors")
public class VendorController {

    private final VendorService vendorService;
    private final TemplateService templateService;

    public VendorController(VendorService vendorService, TemplateService templateService) {
        this.vendorService = vendorService;
        this.templateService = templateService;
    }

    /**
     * Bulk-creates unbooked vendor slots from a vendor template. Planners/admins only, and only
     * on projects the caller can access.
     */
    @PostMapping("/apply-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<List<VendorResponse>> applyTemplate(
            @PathVariable UUID projectId,
            @Valid @RequestBody ApplyTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.applyVendorTemplate(projectId, request.templateId()));
    }

    /** Adds a global directory entry into this project as a new (linked) vendor. */
    @PostMapping("/from-directory")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<VendorResponse> addFromDirectory(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddFromDirectoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vendorService.addFromDirectory(projectId, request.directoryId()));
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<VendorResponse> list(@PathVariable UUID projectId) {
        return vendorService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<VendorResponse> create(@PathVariable UUID projectId,
                                                 @Valid @RequestBody VendorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.create(projectId, request));
    }

    @PutMapping("/{vendorId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public VendorResponse update(@PathVariable UUID projectId,
                                 @PathVariable UUID vendorId,
                                 @Valid @RequestBody VendorRequest request) {
        return vendorService.update(projectId, vendorId, request);
    }

    @DeleteMapping("/{vendorId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID vendorId) {
        vendorService.delete(projectId, vendorId);
        return ResponseEntity.noContent().build();
    }

    // --- Vendor payments (installments against the agreed full amount) ---

    @GetMapping("/{vendorId}/payments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<VendorPaymentResponse> listPayments(@PathVariable UUID projectId,
                                                    @PathVariable UUID vendorId) {
        return vendorService.listPayments(projectId, vendorId);
    }

    @PostMapping("/{vendorId}/payments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<VendorPaymentResponse> addPayment(
            @PathVariable UUID projectId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vendorService.addPayment(projectId, vendorId, request));
    }

    @DeleteMapping("/{vendorId}/payments/{paymentId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID projectId,
                                              @PathVariable UUID vendorId,
                                              @PathVariable UUID paymentId) {
        vendorService.deletePayment(projectId, vendorId, paymentId);
        return ResponseEntity.noContent().build();
    }
}

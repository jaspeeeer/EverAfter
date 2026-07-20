package com.wedding.planner.web;

import com.wedding.planner.dto.TemplateDtos.ChecklistTemplateRequest;
import com.wedding.planner.dto.TemplateDtos.ChecklistTemplateResponse;
import com.wedding.planner.dto.TemplateDtos.VendorTemplateRequest;
import com.wedding.planner.dto.TemplateDtos.VendorTemplateResponse;
import com.wedding.planner.service.TemplateService;
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
 * Platform-wide template catalog. Planners (and admins) may browse — they need the list to apply
 * a template to a project — but only admins may create, edit, or delete templates.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    // --- Checklist templates ---

    @GetMapping("/checklist")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER')")
    public List<ChecklistTemplateResponse> listChecklist() {
        return templateService.listChecklistTemplates();
    }

    @PostMapping("/checklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChecklistTemplateResponse> createChecklist(
            @Valid @RequestBody ChecklistTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.createChecklistTemplate(request));
    }

    @PutMapping("/checklist/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ChecklistTemplateResponse updateChecklist(
            @PathVariable UUID templateId,
            @Valid @RequestBody ChecklistTemplateRequest request) {
        return templateService.updateChecklistTemplate(templateId, request);
    }

    @DeleteMapping("/checklist/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteChecklist(@PathVariable UUID templateId) {
        templateService.deleteChecklistTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    // --- Vendor templates ---

    @GetMapping("/vendors")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER')")
    public List<VendorTemplateResponse> listVendors() {
        return templateService.listVendorTemplates();
    }

    @PostMapping("/vendors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorTemplateResponse> createVendors(
            @Valid @RequestBody VendorTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.createVendorTemplate(request));
    }

    @PutMapping("/vendors/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public VendorTemplateResponse updateVendors(
            @PathVariable UUID templateId,
            @Valid @RequestBody VendorTemplateRequest request) {
        return templateService.updateVendorTemplate(templateId, request);
    }

    @DeleteMapping("/vendors/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVendors(@PathVariable UUID templateId) {
        templateService.deleteVendorTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}

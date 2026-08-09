package com.wedding.planner.web;

import com.wedding.planner.domain.AttachmentOwnerType;
import com.wedding.planner.dto.AttachmentDtos.AttachmentResponse;
import com.wedding.planner.security.AppUserPrincipal;
import com.wedding.planner.service.AttachmentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachments (contracts, receipts, quotes) hung off a vendor, vendor payment, or expense.
 * Every operation — read and write — is gated on {@code canAccess} (admin, managing planner,
 * owning couple): a couple may upload their own paperwork and remove attachments just like the
 * planner. Every write is captured in the activity log, so accidental deletes are auditable.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/vendors/{vendorId}/attachments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<AttachmentResponse> uploadForVendor(
            @PathVariable UUID projectId,
            @PathVariable UUID vendorId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return upload(projectId, AttachmentOwnerType.VENDOR, vendorId, file, principal);
    }

    @PostMapping("/vendor-payments/{paymentId}/attachments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<AttachmentResponse> uploadForVendorPayment(
            @PathVariable UUID projectId,
            @PathVariable UUID paymentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return upload(projectId, AttachmentOwnerType.VENDOR_PAYMENT, paymentId, file, principal);
    }

    @PostMapping("/expenses/{expenseId}/attachments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<AttachmentResponse> uploadForExpense(
            @PathVariable UUID projectId,
            @PathVariable UUID expenseId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return upload(projectId, AttachmentOwnerType.EXPENSE, expenseId, file, principal);
    }

    private ResponseEntity<AttachmentResponse> upload(UUID projectId, AttachmentOwnerType ownerType,
                                                       UUID ownerId, MultipartFile file,
                                                       AppUserPrincipal principal) {
        AttachmentResponse response =
                attachmentService.upload(projectId, ownerType, ownerId, file, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/attachments")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<AttachmentResponse> list(@PathVariable UUID projectId,
                                         @RequestParam(required = false) AttachmentOwnerType ownerType,
                                         @RequestParam(required = false) UUID ownerId) {
        return attachmentService.list(projectId, ownerType, ownerId);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID projectId,
                                                        @PathVariable UUID attachmentId)
            throws IOException {
        AttachmentService.Download download = attachmentService.download(projectId, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.attachment().getFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .contentLength(download.attachment().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.data()));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID attachmentId) {
        attachmentService.delete(projectId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}

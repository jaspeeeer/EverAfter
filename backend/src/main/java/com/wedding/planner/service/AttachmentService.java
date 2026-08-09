package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Attachment;
import com.wedding.planner.domain.AttachmentOwnerType;
import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorPayment;
import com.wedding.planner.dto.AttachmentDtos.AttachmentResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.exception.UnsupportedMediaTypeException;
import com.wedding.planner.repository.AttachmentRepository;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.repository.VendorPaymentRepository;
import com.wedding.planner.repository.VendorRepository;
import com.wedding.planner.storage.AttachmentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * CRUD for attachments (contracts, receipts, quotes) hung off a vendor, vendor payment, or
 * expense. Every write re-verifies that the owner entity actually belongs to {@code projectId} —
 * the same defence-in-depth pattern as {@link VendorService}/{@link ExpenseService} — so an
 * attachment can't be uploaded against an id borrowed from another tenant's project.
 */
@Service
public class AttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");
    private static final int MAX_FILENAME_LENGTH = 200;

    private final AttachmentRepository attachmentRepository;
    private final ProjectRepository projectRepository;
    private final VendorRepository vendorRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AttachmentStorage storage;
    private final ActivityLogService activityLog;
    private final long maxFileBytes;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             ProjectRepository projectRepository,
                             VendorRepository vendorRepository,
                             VendorPaymentRepository vendorPaymentRepository,
                             ExpenseRepository expenseRepository,
                             UserRepository userRepository,
                             AttachmentStorage storage,
                             ActivityLogService activityLog,
                             @Value("${app.attachments.max-file-bytes}") long maxFileBytes) {
        this.attachmentRepository = attachmentRepository;
        this.projectRepository = projectRepository;
        this.vendorRepository = vendorRepository;
        this.vendorPaymentRepository = vendorPaymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.storage = storage;
        this.activityLog = activityLog;
        this.maxFileBytes = maxFileBytes;
    }

    @Transactional
    public AttachmentResponse upload(UUID projectId,
                                     AttachmentOwnerType ownerType,
                                     UUID ownerId,
                                     MultipartFile file,
                                     UUID uploaderId) {
        Project project = requireProject(projectId);
        String ownerLabel = requireOwnerInProject(projectId, ownerType, ownerId);
        validate(file);

        User uploader = uploaderId != null ? userRepository.findById(uploaderId).orElse(null) : null;
        String filename = sanitizeFilename(file.getOriginalFilename());
        // The storage key only needs to be unique and opaque — it does not need to equal the
        // eventual DB row id, which Hibernate assigns at save() time.
        String storageKey = projectId + "/" + UUID.randomUUID();

        try (InputStream in = file.getInputStream()) {
            storage.write(storageKey, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store attachment", e);
        }

        Attachment attachment = new Attachment(
                project, ownerType, ownerId, filename, file.getContentType(),
                file.getSize(), storageKey, uploader);
        Attachment saved = attachmentRepository.save(attachment);

        activityLog.record(projectId, ActivityEntityType.ATTACHMENT, saved.getId(),
                ActivityAction.CREATE, "Attached " + filename + " to " + ownerLabel);
        return AttachmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID projectId, AttachmentOwnerType ownerType, UUID ownerId) {
        requireProject(projectId);
        List<Attachment> rows = (ownerType != null && ownerId != null)
                ? attachmentRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(ownerType, ownerId)
                : attachmentRepository.findByProjectIdOrderByUploadedAtDesc(projectId);
        return rows.stream()
                .filter(a -> a.getProject().getId().equals(projectId))
                .map(AttachmentResponse::from)
                .toList();
    }

    /** Loaded attachment plus an open stream, for the controller to copy to the HTTP response. */
    public record Download(Attachment attachment, InputStream data) {}

    @Transactional(readOnly = true)
    public Download download(UUID projectId, UUID attachmentId) {
        Attachment attachment = requireAttachmentInProject(projectId, attachmentId);
        try {
            return new Download(attachment, storage.read(attachment.getStorageKey()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read attachment", e);
        }
    }

    @Transactional
    public void delete(UUID projectId, UUID attachmentId) {
        Attachment attachment = requireAttachmentInProject(projectId, attachmentId);
        String filename = attachment.getFilename();
        attachmentRepository.delete(attachment);
        deleteFile(attachment.getStorageKey());
        activityLog.record(projectId, ActivityEntityType.ATTACHMENT, attachmentId,
                ActivityAction.DELETE, "Removed " + filename);
    }

    /**
     * Called by {@link VendorService}/{@link ExpenseService} before deleting an owner row, so its
     * attachments (DB rows + stored files) don't outlive it. No activity-log entry here — the
     * owner's own delete already logs a summary line.
     */
    @Transactional
    public void deleteAllFor(AttachmentOwnerType ownerType, UUID ownerId) {
        List<Attachment> rows =
                attachmentRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(ownerType, ownerId);
        attachmentRepository.deleteAll(rows);
        rows.forEach(a -> deleteFile(a.getStorageKey()));
    }

    // --- validation & helpers ---

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        if (file.getSize() > maxFileBytes) {
            throw new BadRequestException(
                    "File exceeds the " + (maxFileBytes / (1024 * 1024)) + " MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedMediaTypeException(
                    "Unsupported file type" + (contentType != null ? ": " + contentType : ""));
        }
    }

    private String sanitizeFilename(String original) {
        String base = original == null || original.isBlank() ? "file" : original;
        // Strip any path components a client might send and cap the length.
        String stripped = base.replaceAll("^.*[/\\\\]", "").trim();
        return stripped.length() > MAX_FILENAME_LENGTH
                ? stripped.substring(0, MAX_FILENAME_LENGTH)
                : stripped;
    }

    private void deleteFile(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete attachment file", e);
        }
    }

    /** Verifies the owner entity belongs to this project; returns a human label for logging. */
    private String requireOwnerInProject(UUID projectId, AttachmentOwnerType ownerType, UUID ownerId) {
        return switch (ownerType) {
            case VENDOR -> {
                Vendor vendor = vendorRepository.findById(ownerId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Vendor", ownerId));
                requireSameProject(vendor.getProject().getId(), projectId, "Vendor", ownerId);
                yield "vendor \"" + vendor.getName() + "\"";
            }
            case VENDOR_PAYMENT -> {
                VendorPayment payment = vendorPaymentRepository.findById(ownerId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Vendor payment", ownerId));
                requireSameProject(
                        payment.getVendor().getProject().getId(), projectId, "Vendor payment", ownerId);
                yield "payment to \"" + payment.getVendor().getName() + "\"";
            }
            case EXPENSE -> {
                Expense expense = expenseRepository.findById(ownerId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Expense", ownerId));
                requireSameProject(expense.getProject().getId(), projectId, "Expense", ownerId);
                yield "expense \"" + expense.getDescription() + "\"";
            }
        };
    }

    private void requireSameProject(UUID actualProjectId, UUID expectedProjectId, String type, UUID id) {
        if (!actualProjectId.equals(expectedProjectId)) {
            throw ResourceNotFoundException.of(type, id);
        }
    }

    private Attachment requireAttachmentInProject(UUID projectId, UUID attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));
        if (!attachment.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }
        return attachment;
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }
}

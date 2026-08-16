package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.Attachment;
import com.wedding.planner.domain.AttachmentOwnerType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Vendor;
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
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for validation and delegation in {@link AttachmentService}. Constructed manually
 * (not {@code @InjectMocks}) because the constructor's {@code maxFileBytes} is a primitive
 * {@code long} that Mockito can't supply a mock for.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final long MAX_BYTES = 10 * 1024 * 1024;

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private VendorPaymentRepository vendorPaymentRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private AttachmentStorage storage;
    @Mock private ActivityLogService activityLog;

    private AttachmentService service;
    private final UUID projectId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepository, projectRepository, vendorRepository,
                vendorPaymentRepository, expenseRepository, userRepository, storage, activityLog,
                MAX_BYTES);
    }

    /**
     * {@code getName()} is stubbed leniently — it's only read on the success path (for the
     * activity-log summary), so the cross-tenant-rejection test would otherwise fail strict-stub
     * verification for a stub it never reaches.
     */
    private Vendor vendorInProject(UUID inProjectId) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(inProjectId);
        Vendor vendor = mock(Vendor.class);
        when(vendor.getProject()).thenReturn(project);
        lenient().when(vendor.getName()).thenReturn("Bloom Florist");
        return vendor;
    }

    @Test
    void uploadWritesToStorageAndSavesARow() throws Exception {
        Vendor vendor = vendorInProject(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "pdf-bytes".getBytes());

        AttachmentResponse response =
                service.upload(projectId, AttachmentOwnerType.VENDOR, vendorId, file, null);

        assertThat(response.filename()).isEqualTo("contract.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        verify(storage).write(anyString(), any());
        verify(attachmentRepository).save(any(Attachment.class));
        verify(activityLog).record(eq(projectId), any(), any(), any(), anyString());
    }

    @Test
    void uploadRejectsOversizedFiles() {
        Vendor vendor = vendorInProject(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        byte[] tooBig = new byte[(int) MAX_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.pdf", "application/pdf", tooBig);

        assertThatThrownBy(() ->
                service.upload(projectId, AttachmentOwnerType.VENDOR, vendorId, file, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadRejectsDisallowedContentTypes() {
        Vendor vendor = vendorInProject(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", "MZ".getBytes());

        assertThatThrownBy(() ->
                service.upload(projectId, AttachmentOwnerType.VENDOR, vendorId, file, null))
                .isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void uploadRejectsAnOwnerFromAnotherProject() {
        UUID otherProjectId = UUID.randomUUID();
        Vendor vendor = vendorInProject(otherProjectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() ->
                service.upload(projectId, AttachmentOwnerType.VENDOR, vendorId, file, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadWithAnOwnerLabelOverrideSkipsTheGenericLookupAndUsesItInTheLog() throws Exception {
        // PROJECT-owned uploads (cover/ceremony/reception photos) all share one owner type with
        // ownerId == projectId, so the generic requireOwnerInProject lookup can't tell them
        // apart — ProjectService always passes an explicit override for exactly this reason.
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "church.jpg", "image/jpeg", "jpeg-bytes".getBytes());

        service.upload(projectId, AttachmentOwnerType.PROJECT, projectId, file, null,
                "the ceremony photo");

        verify(activityLog).record(eq(projectId), any(), any(), any(),
                eq("Attached church.jpg to the ceremony photo"));
        verify(vendorRepository, never()).findById(any());
    }

    @Test
    void deleteRemovesTheRowAndTheStoredFile() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = mock(Attachment.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        when(attachment.getProject()).thenReturn(project);
        when(attachment.getStorageKey()).thenReturn(projectId + "/" + attachmentId);
        when(attachment.getFilename()).thenReturn("contract.pdf");
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.delete(projectId, attachmentId);

        verify(attachmentRepository).delete(attachment);
        verify(storage).delete(projectId + "/" + attachmentId);
    }

    @Test
    void deleteAllForRemovesEveryMatchingRowAndFile() throws Exception {
        Attachment a1 = mock(Attachment.class);
        Attachment a2 = mock(Attachment.class);
        when(a1.getStorageKey()).thenReturn("k1");
        when(a2.getStorageKey()).thenReturn("k2");
        when(attachmentRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(
                AttachmentOwnerType.VENDOR, vendorId))
                .thenReturn(List.of(a1, a2));

        service.deleteAllFor(AttachmentOwnerType.VENDOR, vendorId);

        verify(attachmentRepository).deleteAll(List.of(a1, a2));
        verify(storage).delete("k1");
        verify(storage).delete("k2");
    }

    @Test
    void deleteAllForIsANoOpWhenNothingMatches() throws Exception {
        when(attachmentRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(
                AttachmentOwnerType.EXPENSE, vendorId))
                .thenReturn(List.of());

        service.deleteAllFor(AttachmentOwnerType.EXPENSE, vendorId);

        verify(attachmentRepository, times(1)).deleteAll(List.of());
        verify(storage, never()).delete(anyString());
    }

    // Mockito's any() overload resolution needs a little help for ActivityLogService.record's
    // enum-typed params — this local matches ArgumentMatchers.eq semantics via a static import
    // alias so the test above reads cleanly.
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}

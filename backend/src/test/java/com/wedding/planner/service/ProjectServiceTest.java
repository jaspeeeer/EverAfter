package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.AttachmentOwnerType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.AttachmentDtos.AttachmentResponse;
import com.wedding.planner.dto.ProjectRequest;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.security.AppUserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for role-scoped project listing and planner resolution on create.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ActivityLogService activityLog;

    @InjectMocks
    private ProjectService projectService;

    private AppUserPrincipal principal(UUID id, String role) {
        return new AppUserPrincipal(id, id + "@wedding.test", "hash", true,
                List.<GrantedAuthority>of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void adminListingReturnsEveryProject() {
        projectService.listVisible(principal(UUID.randomUUID(), "ROLE_ADMIN"));

        verify(projectRepository).findAll();
        verify(projectRepository, never()).findByPlannerId(any());
        verify(projectRepository, never()).findByOwnerId(any());
    }

    @Test
    void plannerListingReturnsOnlyOwnProjects() {
        UUID plannerId = UUID.randomUUID();
        when(projectRepository.findByPlannerId(plannerId)).thenReturn(List.of());

        projectService.listVisible(principal(plannerId, "ROLE_PLANNER"));

        verify(projectRepository).findByPlannerId(plannerId);
        verify(projectRepository, never()).findAll();
        verify(projectRepository, never()).findByOwnerId(any());
    }

    @Test
    void coupleListingReturnsOnlyTheirProject() {
        UUID coupleId = UUID.randomUUID();
        when(projectRepository.findByOwnerId(coupleId)).thenReturn(Optional.empty());

        projectService.listVisible(principal(coupleId, "ROLE_USER"));

        verify(projectRepository).findByOwnerId(coupleId);
        verify(projectRepository, never()).findAll();
        verify(projectRepository, never()).findByPlannerId(any());
    }

    @Test
    void plannerCreatingProjectBecomesTheManagingPlanner() {
        UUID plannerId = UUID.randomUUID();
        User plannerUser = org.mockito.Mockito.mock(User.class);
        when(userRepository.findById(plannerId)).thenReturn(Optional.of(plannerUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectRequest request = new ProjectRequest(
                "Our Wedding", null, null, null, null, null, null, null, null, null, null, false, null,
                null, null, null, null, null, null, null);
        projectService.create(request, principal(plannerId, "ROLE_PLANNER"));

        // The planner is loaded by their own id, never from the request.
        verify(userRepository).findById(plannerId);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void adminCreatingProjectWithoutPlannerIdIsRejected() {
        ProjectRequest request = new ProjectRequest(
                "Admin Wedding", null, null, null, null, null, null, null, null, null, null, false, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() ->
                projectService.create(request, principal(UUID.randomUUID(), "ROLE_ADMIN")))
                .isInstanceOf(BadRequestException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void adminCreatingProjectUsesRequestedPlanner() {
        UUID adminId = UUID.randomUUID();
        UUID targetPlannerId = UUID.randomUUID();
        User plannerUser = org.mockito.Mockito.mock(User.class);
        when(userRepository.findById(targetPlannerId)).thenReturn(Optional.of(plannerUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectRequest request =
                new ProjectRequest("Assigned Wedding", null, null, targetPlannerId, null, null,
                        null, null, null, null, null, false, null,
                        null, null, null, null, null, null, null);
        projectService.create(request, principal(adminId, "ROLE_ADMIN"));

        verify(userRepository).findById(targetPlannerId);
        assertThat(request.plannerId()).isEqualTo(targetPlannerId);
    }

    // --- Photo slots (cover / ceremony / reception) ---

    private interface PhotoSetter {
        void set(UUID projectId, MultipartFile file, UUID uploaderId);
    }

    private interface PhotoRemover {
        void remove(UUID projectId);
    }

    private Project projectWithId(UUID projectId) {
        Project project = new Project("Photo Wedding", org.mockito.Mockito.mock(User.class));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        return project;
    }

    private AttachmentResponse uploadedAttachment(UUID id) {
        return new AttachmentResponse(id, UUID.randomUUID(), AttachmentOwnerType.PROJECT,
                UUID.randomUUID(), "photo.jpg", "image/jpeg", 100L, null, null, null);
    }

    /**
     * Shared body for the set-first-time case, run once per slot below. Asserting the exact
     * activity-log label locks in the fix for a real bug: before {@code upload()} accepted an
     * {@code ownerLabelOverride}, every PROJECT-owned upload (cover, ceremony, reception alike)
     * would log itself as "the cover photo" regardless of which slot it actually went into,
     * since {@code requireOwnerInProject}'s generic PROJECT case can't tell the slots apart.
     */
    private void verifySettingForTheFirstTime(PhotoSetter setter, Function<Project, UUID> getId, String label) {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID newAttachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());
        when(attachmentService.upload(
                projectId, AttachmentOwnerType.PROJECT, projectId, file, null, "the " + label))
                .thenReturn(uploadedAttachment(newAttachmentId));

        setter.set(projectId, file, null);

        assertThat(getId.apply(project)).isEqualTo(newAttachmentId);
        verify(attachmentService, never()).delete(any(), any());
        verify(activityLog).record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Updated the " + label);
    }

    private void verifySettingOverAnExisting(PhotoSetter setter, java.util.function.BiConsumer<Project, UUID> setId,
                                             Function<Project, UUID> getId, String label) {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID oldAttachmentId = UUID.randomUUID();
        setId.accept(project, oldAttachmentId);
        UUID newAttachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());
        when(attachmentService.upload(
                projectId, AttachmentOwnerType.PROJECT, projectId, file, null, "the " + label))
                .thenReturn(uploadedAttachment(newAttachmentId));

        setter.set(projectId, file, null);

        assertThat(getId.apply(project)).isEqualTo(newAttachmentId);
        verify(attachmentService).delete(projectId, oldAttachmentId);
    }

    private void verifyRemoving(PhotoRemover remover, java.util.function.BiConsumer<Project, UUID> setId,
                               Function<Project, UUID> getId, String label) {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID attachmentId = UUID.randomUUID();
        setId.accept(project, attachmentId);

        remover.remove(projectId);

        assertThat(getId.apply(project)).isNull();
        verify(attachmentService).delete(projectId, attachmentId);
        verify(activityLog).record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Removed the " + label);
    }

    private void verifyRemovingNonExistentIs404(PhotoRemover remover) {
        UUID projectId = UUID.randomUUID();
        projectWithId(projectId);

        assertThatThrownBy(() -> remover.remove(projectId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentService, never()).delete(any(), any());
    }

    @Test
    void settingACoverForTheFirstTimeStoresTheAttachmentIdAndLogsItCorrectly() {
        verifySettingForTheFirstTime(
                (id, file, uploader) -> projectService.setCover(id, file, uploader),
                Project::getCoverAttachmentId, "cover photo");
    }

    @Test
    void settingACoverOverAnExistingOneDeletesThePriorAttachment() {
        verifySettingOverAnExisting(
                (id, file, uploader) -> projectService.setCover(id, file, uploader),
                Project::setCoverAttachmentId, Project::getCoverAttachmentId, "cover photo");
    }

    @Test
    void removingACoverClearsTheFkAndDeletesTheAttachment() {
        verifyRemoving(projectService::removeCover, Project::setCoverAttachmentId,
                Project::getCoverAttachmentId, "cover photo");
    }

    @Test
    void removingANonExistentCoverIs404NotANoOp() {
        verifyRemovingNonExistentIs404(projectService::removeCover);
    }

    @Test
    void settingACeremonyPhotoForTheFirstTimeStoresTheAttachmentIdAndLogsItCorrectly() {
        verifySettingForTheFirstTime(
                (id, file, uploader) -> projectService.setCeremonyPhoto(id, file, uploader),
                Project::getCeremonyPhotoAttachmentId, "ceremony photo");
    }

    @Test
    void settingACeremonyPhotoOverAnExistingOneDeletesThePriorAttachment() {
        verifySettingOverAnExisting(
                (id, file, uploader) -> projectService.setCeremonyPhoto(id, file, uploader),
                Project::setCeremonyPhotoAttachmentId, Project::getCeremonyPhotoAttachmentId,
                "ceremony photo");
    }

    @Test
    void removingACeremonyPhotoClearsTheFkAndDeletesTheAttachment() {
        verifyRemoving(projectService::removeCeremonyPhoto, Project::setCeremonyPhotoAttachmentId,
                Project::getCeremonyPhotoAttachmentId, "ceremony photo");
    }

    @Test
    void removingANonExistentCeremonyPhotoIs404NotANoOp() {
        verifyRemovingNonExistentIs404(projectService::removeCeremonyPhoto);
    }

    @Test
    void settingAReceptionPhotoForTheFirstTimeStoresTheAttachmentIdAndLogsItCorrectly() {
        verifySettingForTheFirstTime(
                (id, file, uploader) -> projectService.setReceptionPhoto(id, file, uploader),
                Project::getReceptionPhotoAttachmentId, "reception photo");
    }

    @Test
    void settingAReceptionPhotoOverAnExistingOneDeletesThePriorAttachment() {
        verifySettingOverAnExisting(
                (id, file, uploader) -> projectService.setReceptionPhoto(id, file, uploader),
                Project::setReceptionPhotoAttachmentId, Project::getReceptionPhotoAttachmentId,
                "reception photo");
    }

    @Test
    void removingAReceptionPhotoClearsTheFkAndDeletesTheAttachment() {
        verifyRemoving(projectService::removeReceptionPhoto, Project::setReceptionPhotoAttachmentId,
                Project::getReceptionPhotoAttachmentId, "reception photo");
    }

    @Test
    void removingANonExistentReceptionPhotoIs404NotANoOp() {
        verifyRemovingNonExistentIs404(projectService::removeReceptionPhoto);
    }

    @Test
    void settingAnAttireMenPhotoForTheFirstTimeStoresTheAttachmentIdAndLogsItCorrectly() {
        verifySettingForTheFirstTime(
                (id, file, uploader) -> projectService.setAttireMenPhoto(id, file, uploader),
                Project::getAttireMenPhotoAttachmentId, "attire (men) photo");
    }

    @Test
    void settingAnAttireMenPhotoOverAnExistingOneDeletesThePriorAttachment() {
        verifySettingOverAnExisting(
                (id, file, uploader) -> projectService.setAttireMenPhoto(id, file, uploader),
                Project::setAttireMenPhotoAttachmentId, Project::getAttireMenPhotoAttachmentId,
                "attire (men) photo");
    }

    @Test
    void removingAnAttireMenPhotoClearsTheFkAndDeletesTheAttachment() {
        verifyRemoving(projectService::removeAttireMenPhoto, Project::setAttireMenPhotoAttachmentId,
                Project::getAttireMenPhotoAttachmentId, "attire (men) photo");
    }

    @Test
    void removingANonExistentAttireMenPhotoIs404NotANoOp() {
        verifyRemovingNonExistentIs404(projectService::removeAttireMenPhoto);
    }

    @Test
    void settingAnAttireWomenPhotoForTheFirstTimeStoresTheAttachmentIdAndLogsItCorrectly() {
        verifySettingForTheFirstTime(
                (id, file, uploader) -> projectService.setAttireWomenPhoto(id, file, uploader),
                Project::getAttireWomenPhotoAttachmentId, "attire (women) photo");
    }

    @Test
    void settingAnAttireWomenPhotoOverAnExistingOneDeletesThePriorAttachment() {
        verifySettingOverAnExisting(
                (id, file, uploader) -> projectService.setAttireWomenPhoto(id, file, uploader),
                Project::setAttireWomenPhotoAttachmentId, Project::getAttireWomenPhotoAttachmentId,
                "attire (women) photo");
    }

    @Test
    void removingAnAttireWomenPhotoClearsTheFkAndDeletesTheAttachment() {
        verifyRemoving(projectService::removeAttireWomenPhoto, Project::setAttireWomenPhotoAttachmentId,
                Project::getAttireWomenPhotoAttachmentId, "attire (women) photo");
    }

    @Test
    void removingANonExistentAttireWomenPhotoIs404NotANoOp() {
        verifyRemovingNonExistentIs404(projectService::removeAttireWomenPhoto);
    }
}

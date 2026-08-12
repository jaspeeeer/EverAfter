package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wedding.planner.audit.ActivityLogService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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

        ProjectRequest request = new ProjectRequest("Our Wedding", null, null, null, null, null, null, null, null, false, null);
        projectService.create(request, principal(plannerId, "ROLE_PLANNER"));

        // The planner is loaded by their own id, never from the request.
        verify(userRepository).findById(plannerId);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void adminCreatingProjectWithoutPlannerIdIsRejected() {
        ProjectRequest request = new ProjectRequest("Admin Wedding", null, null, null, null, null, null, null, null, false, null);

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
                new ProjectRequest("Assigned Wedding", null, null, targetPlannerId, null, null, null, null, null, false, null);
        projectService.create(request, principal(adminId, "ROLE_ADMIN"));

        verify(userRepository).findById(targetPlannerId);
        assertThat(request.plannerId()).isEqualTo(targetPlannerId);
    }

    // --- Cover photo ---

    private Project projectWithId(UUID projectId) {
        Project project = new Project("Cover Wedding", org.mockito.Mockito.mock(User.class));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        return project;
    }

    private AttachmentResponse uploadedAttachment(UUID id) {
        return new AttachmentResponse(id, UUID.randomUUID(), AttachmentOwnerType.PROJECT,
                UUID.randomUUID(), "cover.jpg", "image/jpeg", 100L, null, null, null);
    }

    @Test
    void settingACoverForTheFirstTimeStoresTheAttachmentIdAndDeletesNothing() {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID newAttachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "x".getBytes());
        when(attachmentService.upload(projectId, AttachmentOwnerType.PROJECT, projectId, file, null))
                .thenReturn(uploadedAttachment(newAttachmentId));

        projectService.setCover(projectId, file, null);

        assertThat(project.getCoverAttachmentId()).isEqualTo(newAttachmentId);
        verify(attachmentService, never()).delete(any(), any());
    }

    @Test
    void settingACoverOverAnExistingOneDeletesThePriorAttachment() {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID oldAttachmentId = UUID.randomUUID();
        project.setCoverAttachmentId(oldAttachmentId);
        UUID newAttachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "x".getBytes());
        when(attachmentService.upload(projectId, AttachmentOwnerType.PROJECT, projectId, file, null))
                .thenReturn(uploadedAttachment(newAttachmentId));

        projectService.setCover(projectId, file, null);

        assertThat(project.getCoverAttachmentId()).isEqualTo(newAttachmentId);
        verify(attachmentService).delete(projectId, oldAttachmentId);
    }

    @Test
    void removingACoverClearsTheFkAndDeletesTheAttachment() {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        UUID coverId = UUID.randomUUID();
        project.setCoverAttachmentId(coverId);

        projectService.removeCover(projectId);

        assertThat(project.getCoverAttachmentId()).isNull();
        verify(attachmentService).delete(projectId, coverId);
    }

    @Test
    void removingANonExistentCoverIs404NotANoOp() {
        UUID projectId = UUID.randomUUID();
        projectWithId(projectId);

        assertThatThrownBy(() -> projectService.removeCover(projectId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentService, never()).delete(any(), any());
    }
}

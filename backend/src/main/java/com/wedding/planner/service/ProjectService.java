package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.AttachmentOwnerType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.AttachmentDtos.AttachmentResponse;
import com.wedding.planner.dto.ProjectRequest;
import com.wedding.planner.dto.ProjectResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.security.AppUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project CRUD plus role-scoped listing.
 *
 * <p>Single-project reads/writes are gated by {@code @PreAuthorize} in the controller (via
 * {@link com.wedding.planner.security.ProjectSecurity}). Listing enforces isolation here: the set
 * of visible projects is derived from the caller's role.
 */
@Service
public class ProjectService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_PLANNER = "ROLE_PLANNER";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final ActivityLogService activityLog;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository,
                          AttachmentService attachmentService, ActivityLogService activityLog) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
        this.activityLog = activityLog;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, AppUserPrincipal principal) {
        User planner = resolvePlanner(request, principal);

        Project project = new Project(request.name(), planner);
        project.setWeddingDate(request.weddingDate());
        project.setTotalBudget(request.totalBudget());
        project.setVenueName(request.venueName());
        project.setVenueAddress(request.venueAddress());
        project.setCeremonyTime(request.ceremonyTime());
        project.setReceptionTime(request.receptionTime());
        project.setAllowGuestPartySize(request.allowGuestPartySize());
        project.setMaxPartySize(request.maxPartySize());
        attachOwnerIfPresent(project, request.ownerEmail());

        Project saved = projectRepository.save(project);
        activityLog.record(saved.getId(), ActivityEntityType.PROJECT, saved.getId(),
                ActivityAction.CREATE, "Created project \"" + saved.getName() + "\"");
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId) {
        return ProjectResponse.from(findProject(projectId));
    }

    /** Returns only the projects the caller is allowed to see, based on their role. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listVisible(AppUserPrincipal principal) {
        List<Project> projects;
        if (hasRole(principal, ROLE_ADMIN)) {
            projects = projectRepository.findAll();
        } else if (hasRole(principal, ROLE_PLANNER)) {
            projects = projectRepository.findByPlannerId(principal.getId());
        } else {
            projects = projectRepository.findByOwnerId(principal.getId())
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional
    public ProjectResponse update(UUID projectId, ProjectRequest request) {
        Project project = findProject(projectId);
        project.setName(request.name());
        project.setWeddingDate(request.weddingDate());
        project.setTotalBudget(request.totalBudget());
        project.setVenueName(request.venueName());
        project.setVenueAddress(request.venueAddress());
        project.setCeremonyTime(request.ceremonyTime());
        project.setReceptionTime(request.receptionTime());
        project.setAllowGuestPartySize(request.allowGuestPartySize());
        project.setMaxPartySize(request.maxPartySize());
        activityLog.record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Updated project details");
        return ProjectResponse.from(project);
    }

    /**
     * Sets (or replaces) the project's cover photo. A project has exactly one cover — any prior
     * one is hard-deleted (file + row) once the new one is in place, so there's never an orphan
     * and never two "current" covers.
     */
    @Transactional
    public ProjectResponse setCover(UUID projectId, MultipartFile file, UUID uploaderId) {
        Project project = findProject(projectId);
        UUID previousCoverId = project.getCoverAttachmentId();

        AttachmentResponse uploaded =
                attachmentService.upload(projectId, AttachmentOwnerType.PROJECT, projectId, file, uploaderId);
        project.setCoverAttachmentId(uploaded.id());

        if (previousCoverId != null) {
            attachmentService.delete(projectId, previousCoverId);
        }

        activityLog.record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Updated project cover photo");
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse removeCover(UUID projectId) {
        Project project = findProject(projectId);
        UUID coverId = project.getCoverAttachmentId();
        if (coverId == null) {
            throw ResourceNotFoundException.of("Project cover", projectId);
        }
        // Clear the FK before deleting the attachment row it points to — Hibernate flushes
        // updates ahead of deletes within the same transaction, so the FK's own
        // ON DELETE SET NULL is never actually exercised here, but nulling explicitly keeps this
        // correct regardless of flush ordering.
        project.setCoverAttachmentId(null);
        attachmentService.delete(projectId, coverId);

        activityLog.record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Removed project cover photo");
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID projectId) {
        Project project = findProject(projectId);
        // No activity-log entry for the project's own delete: the log-of-record disappears with
        // the project's cascade anyway (activity_log.project_id is ON DELETE CASCADE), so logging
        // it would be immediately erased. Deleting tasks/vendors/expenses/guests/timeline_events
        // is JPA's CascadeType.ALL on Project's five child collections (V1-V5), not a DB cascade —
        // those five FKs only became ON DELETE CASCADE in V18, added so a project with
        // soft-deleted children still purges cleanly (@SQLRestriction hides tombstones from JPA's
        // own collection traversal, so the DB cascade is what actually removes them now).
        projectRepository.delete(project);
    }

    private User resolvePlanner(ProjectRequest request, AppUserPrincipal principal) {
        if (hasRole(principal, ROLE_ADMIN)) {
            if (request.plannerId() == null) {
                throw new BadRequestException("plannerId is required when an admin creates a project");
            }
            return userRepository.findById(request.plannerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Planner", request.plannerId()));
        }
        // A planner always manages their own projects.
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", principal.getId()));
    }

    private void attachOwnerIfPresent(Project project, String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            return;
        }
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("User", ownerEmail));
        project.setOwner(owner);
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private boolean hasRole(AppUserPrincipal principal, String role) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }
}

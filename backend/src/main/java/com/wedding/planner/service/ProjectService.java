package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
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
    private final ActivityLogService activityLog;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository,
                          ActivityLogService activityLog) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, AppUserPrincipal principal) {
        User planner = resolvePlanner(request, principal);

        Project project = new Project(request.name(), planner);
        project.setWeddingDate(request.weddingDate());
        project.setTotalBudget(request.totalBudget());
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
        activityLog.record(projectId, ActivityEntityType.PROJECT, projectId,
                ActivityAction.UPDATE, "Updated project details");
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID projectId) {
        Project project = findProject(projectId);
        // Log BEFORE the delete: the FK is ON DELETE CASCADE, so the row disappears with the
        // project. Recording the delete for its own log-of-record is intentionally moot.
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

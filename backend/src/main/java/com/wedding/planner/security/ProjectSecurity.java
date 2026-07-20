package com.wedding.planner.security;

import com.wedding.planner.domain.Project;
import com.wedding.planner.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central authorization logic for project-scoped access, referenced from {@code @PreAuthorize}
 * expressions as {@code @projectSecurity}. This is where the RBAC data-isolation rules live:
 *
 * <ul>
 *   <li><b>ADMIN</b> — may access and manage every project.</li>
 *   <li><b>PLANNER</b> — may access/manage only projects they manage.</li>
 *   <li><b>USER (couple)</b> — may access/edit only the project they own, but not manage
 *       (delete/reassign) it.</li>
 * </ul>
 */
@Component("projectSecurity")
public class ProjectSecurity {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ProjectRepository projectRepository;

    public ProjectSecurity(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /** View/edit a project: admin, the managing planner, or the owning couple. */
    @Transactional(readOnly = true)
    public boolean canAccess(UUID projectId, Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_ADMIN)) {
            return true;
        }
        return projectRepository.findById(projectId)
                .map(project -> isManagingPlanner(project, principal.getId())
                        || isOwningCouple(project, principal.getId()))
                .orElse(false);
    }

    /** Manage a project (delete / reassign): admin or the managing planner only. */
    @Transactional(readOnly = true)
    public boolean canManage(UUID projectId, Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_ADMIN)) {
            return true;
        }
        return projectRepository.findById(projectId)
                .map(project -> isManagingPlanner(project, principal.getId()))
                .orElse(false);
    }

    private boolean isManagingPlanner(Project project, UUID userId) {
        return project.getPlanner() != null && userId.equals(project.getPlanner().getId());
    }

    private boolean isOwningCouple(Project project, UUID userId) {
        return project.getOwner() != null && userId.equals(project.getOwner().getId());
    }

    private AppUserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal p) {
            return p;
        }
        return null;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }
}

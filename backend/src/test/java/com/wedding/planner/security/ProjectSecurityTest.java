package com.wedding.planner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for the RBAC data-isolation core. Verifies that admins see everything, planners and
 * couples are scoped to their own projects, and cross-tenant access is denied.
 */
@ExtendWith(MockitoExtension.class)
class ProjectSecurityTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectSecurity projectSecurity;

    private final UUID plannerId = UUID.randomUUID();
    private final UUID otherPlannerId = UUID.randomUUID();
    private final UUID coupleId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private Authentication auth(UUID userId, String role) {
        AppUserPrincipal principal = new AppUserPrincipal(
                userId, userId + "@wedding.test", "hash", true,
                List.<GrantedAuthority>of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Project projectWith(UUID plannerId, UUID ownerId) {
        User planner = org.mockito.Mockito.mock(User.class);
        lenient().when(planner.getId()).thenReturn(plannerId);
        Project project = org.mockito.Mockito.mock(Project.class);
        lenient().when(project.getPlanner()).thenReturn(planner);
        if (ownerId != null) {
            User owner = org.mockito.Mockito.mock(User.class);
            lenient().when(owner.getId()).thenReturn(ownerId);
            lenient().when(project.getOwner()).thenReturn(owner);
        }
        return project;
    }

    // --- canAccess ---

    @Test
    void adminCanAccessAnyProject() {
        assertThat(projectSecurity.canAccess(projectId, auth(strangerId, "ROLE_ADMIN"))).isTrue();
    }

    @Test
    void managingPlannerCanAccessOwnProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canAccess(projectId, auth(plannerId, "ROLE_PLANNER"))).isTrue();
    }

    @Test
    void otherPlannerCannotAccessForeignProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canAccess(projectId, auth(otherPlannerId, "ROLE_PLANNER"))).isFalse();
    }

    @Test
    void owningCoupleCanAccessTheirProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canAccess(projectId, auth(coupleId, "ROLE_USER"))).isTrue();
    }

    @Test
    void strangerCoupleCannotAccessOthersProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canAccess(projectId, auth(strangerId, "ROLE_USER"))).isFalse();
    }

    @Test
    void deniesAccessWhenProjectMissing() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        assertThat(projectSecurity.canAccess(projectId, auth(plannerId, "ROLE_PLANNER"))).isFalse();
    }

    @Test
    void deniesAccessForNullAuthentication() {
        assertThat(projectSecurity.canAccess(projectId, null)).isFalse();
    }

    // --- canManage ---

    @Test
    void adminCanManageAnyProject() {
        assertThat(projectSecurity.canManage(projectId, auth(strangerId, "ROLE_ADMIN"))).isTrue();
    }

    @Test
    void managingPlannerCanManageOwnProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canManage(projectId, auth(plannerId, "ROLE_PLANNER"))).isTrue();
    }

    @Test
    void owningCoupleCannotManageProject() {
        Project project = projectWith(plannerId, coupleId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        assertThat(projectSecurity.canManage(projectId, auth(coupleId, "ROLE_USER"))).isFalse();
    }
}

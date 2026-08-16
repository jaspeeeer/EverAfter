package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.EntourageMember;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberRequest;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberResponse;
import com.wedding.planner.dto.EntourageDtos.PublicEntourageMember;
import com.wedding.planner.repository.EntourageMemberRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the entourage list's move-up/move-down reordering and next-sort-order append.
 * Cross-tenant ownership checks (the {@code requireMemberInProject} pattern shared with
 * {@code TaskService}/{@code GuestService}) are covered at the HTTP level in
 * {@code InvitationRsvpAdminIntegrationTest}, where entities have real persisted ids.
 */
@ExtendWith(MockitoExtension.class)
class EntourageServiceTest {

    @Mock
    private EntourageMemberRepository entourageMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ActivityLogService activityLog;

    @InjectMocks
    private EntourageService entourageService;

    /** A project with a real id set — requireMemberInProject compares against it. */
    private Project newProject(UUID projectId) {
        Project project = new Project("Entourage Wedding", org.mockito.Mockito.mock(User.class));
        ReflectionTestUtils.setField(project, "id", projectId);
        return project;
    }

    /** Same as {@link #newProject}, plus stubs {@code findById} — for methods that load it. */
    private Project projectWithId(UUID projectId) {
        Project project = newProject(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        return project;
    }

    private EntourageMember plainMember(Project project, String role, String name, int sortOrder) {
        EntourageMember member = new EntourageMember(role, name, sortOrder);
        member.setProject(project);
        return member;
    }

    /** Also stubs {@code findById} — use for the member whose id is the primary argument. */
    private EntourageMember targetMember(Project project, String role, String name, int sortOrder) {
        EntourageMember member = plainMember(project, role, name, sortOrder);
        when(entourageMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        return member;
    }

    @Test
    void addAppendsWithTheNextSortOrder() {
        UUID projectId = UUID.randomUUID();
        Project project = projectWithId(projectId);
        EntourageMember existing = plainMember(project, "Best Man", "First", 0);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(existing));
        when(entourageMemberRepository.save(org.mockito.ArgumentMatchers.any(EntourageMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EntourageMemberResponse response =
                entourageService.add(projectId, new EntourageMemberRequest("Groomsman", "Second"));

        assertThat(response.sortOrder()).isEqualTo(1);
        assertThat(response.role()).isEqualTo("Groomsman");
    }

    @Test
    void addToAnEmptyListStartsAtZero() {
        UUID projectId = UUID.randomUUID();
        projectWithId(projectId);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of());
        when(entourageMemberRepository.save(org.mockito.ArgumentMatchers.any(EntourageMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EntourageMemberResponse response =
                entourageService.add(projectId, new EntourageMemberRequest("Best Man", "First"));

        assertThat(response.sortOrder()).isEqualTo(0);
    }

    @Test
    void moveUpSwapsSortOrderWithThePreviousMember() {
        UUID projectId = UUID.randomUUID();
        Project project = newProject(projectId);
        EntourageMember first = plainMember(project, "Best Man", "First", 0);
        EntourageMember second = targetMember(project, "Groomsman", "Second", 1);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(first, second));

        entourageService.moveUp(projectId, second.getId());

        assertThat(second.getSortOrder()).isEqualTo(0);
        assertThat(first.getSortOrder()).isEqualTo(1);
    }

    @Test
    void moveUpOnTheFirstMemberIsANoOp() {
        UUID projectId = UUID.randomUUID();
        Project project = newProject(projectId);
        EntourageMember first = targetMember(project, "Best Man", "First", 0);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(first));

        entourageService.moveUp(projectId, first.getId());

        assertThat(first.getSortOrder()).isEqualTo(0);
    }

    @Test
    void moveDownOnTheLastMemberIsANoOp() {
        UUID projectId = UUID.randomUUID();
        Project project = newProject(projectId);
        EntourageMember first = plainMember(project, "Best Man", "First", 0);
        EntourageMember second = targetMember(project, "Groomsman", "Second", 1);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(first, second));

        entourageService.moveDown(projectId, second.getId());

        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(first.getSortOrder()).isEqualTo(0);
    }

    @Test
    void publicViewOmitsIdsAndKeepsOrder() {
        UUID projectId = UUID.randomUUID();
        EntourageMember first = plainMember(null, "Best Man", "First", 0);
        EntourageMember second = plainMember(null, "Maid of Honor", "Second", 1);
        when(entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(first, second));

        List<PublicEntourageMember> publicView = entourageService.listForPublicView(projectId);

        assertThat(publicView).containsExactly(
                new PublicEntourageMember("Best Man", "First"),
                new PublicEntourageMember("Maid of Honor", "Second"));
    }
}

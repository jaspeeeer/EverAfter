package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.EntourageMember;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberRequest;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberResponse;
import com.wedding.planner.dto.EntourageDtos.PublicEntourageMember;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.EntourageMemberRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for a project's entourage (wedding party) list — a simple ordered {@code {role, name}}
 * list, not a full CRM resource like {@link GuestService}. See {@link TaskService} for the
 * project-scoping pattern that keeps authorization sound.
 */
@Service
public class EntourageService {

    private final EntourageMemberRepository entourageMemberRepository;
    private final ProjectRepository projectRepository;
    private final ActivityLogService activityLog;

    public EntourageService(EntourageMemberRepository entourageMemberRepository,
                            ProjectRepository projectRepository,
                            ActivityLogService activityLog) {
        this.entourageMemberRepository = entourageMemberRepository;
        this.projectRepository = projectRepository;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<EntourageMemberResponse> list(UUID projectId) {
        requireProject(projectId);
        return orderedMembers(projectId).stream().map(EntourageMemberResponse::from).toList();
    }

    /** Ordered, no-id shape for the public RSVP page. */
    @Transactional(readOnly = true)
    public List<PublicEntourageMember> listForPublicView(UUID projectId) {
        return orderedMembers(projectId).stream().map(PublicEntourageMember::from).toList();
    }

    @Transactional
    public EntourageMemberResponse add(UUID projectId, EntourageMemberRequest request) {
        Project project = requireProject(projectId);
        int nextOrder = orderedMembers(projectId).stream()
                .mapToInt(EntourageMember::getSortOrder)
                .max()
                .orElse(-1) + 1;
        EntourageMember member = new EntourageMember(request.role(), request.name(), nextOrder);
        project.addEntourageMember(member);
        EntourageMember saved = entourageMemberRepository.save(member);
        activityLog.record(projectId, ActivityEntityType.ENTOURAGE_MEMBER, saved.getId(),
                ActivityAction.CREATE, "Added \"" + saved.getName() + "\" (" + saved.getRole() + ") to the entourage");
        return EntourageMemberResponse.from(saved);
    }

    @Transactional
    public EntourageMemberResponse update(UUID projectId, UUID memberId, EntourageMemberRequest request) {
        EntourageMember member = requireMemberInProject(projectId, memberId);
        member.setRole(request.role());
        member.setName(request.name());
        activityLog.record(projectId, ActivityEntityType.ENTOURAGE_MEMBER, memberId,
                ActivityAction.UPDATE, "Updated entourage entry \"" + member.getName() + "\"");
        return EntourageMemberResponse.from(member);
    }

    @Transactional
    public void remove(UUID projectId, UUID memberId) {
        EntourageMember member = requireMemberInProject(projectId, memberId);
        String name = member.getName();
        Project project = member.getProject();
        project.removeEntourageMember(member);
        entourageMemberRepository.delete(member);
        activityLog.record(projectId, ActivityEntityType.ENTOURAGE_MEMBER, memberId,
                ActivityAction.DELETE, "Removed \"" + name + "\" from the entourage");
    }

    @Transactional
    public EntourageMemberResponse moveUp(UUID projectId, UUID memberId) {
        return move(projectId, memberId, -1);
    }

    @Transactional
    public EntourageMemberResponse moveDown(UUID projectId, UUID memberId) {
        return move(projectId, memberId, 1);
    }

    /** Swaps sortOrder with the adjacent member in the given direction; a no-op at either end. */
    private EntourageMemberResponse move(UUID projectId, UUID memberId, int direction) {
        EntourageMember member = requireMemberInProject(projectId, memberId);
        List<EntourageMember> ordered = orderedMembers(projectId);
        int index = ordered.indexOf(member);
        int swapWith = index + direction;
        if (swapWith < 0 || swapWith >= ordered.size()) {
            return EntourageMemberResponse.from(member);
        }
        EntourageMember neighbor = ordered.get(swapWith);
        int memberOrder = member.getSortOrder();
        member.setSortOrder(neighbor.getSortOrder());
        neighbor.setSortOrder(memberOrder);
        return EntourageMemberResponse.from(member);
    }

    private List<EntourageMember> orderedMembers(UUID projectId) {
        return entourageMemberRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private EntourageMember requireMemberInProject(UUID projectId, UUID memberId) {
        EntourageMember member = entourageMemberRepository.findById(memberId)
                .orElseThrow(() -> ResourceNotFoundException.of("Entourage member", memberId));
        if (!member.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Entourage member", memberId);
        }
        return member;
    }
}

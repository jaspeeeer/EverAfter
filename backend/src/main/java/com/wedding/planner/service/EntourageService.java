package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.EntourageMember;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberRequest;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberResponse;
import com.wedding.planner.dto.EntourageDtos.ImportFromGuestsResult;
import com.wedding.planner.dto.EntourageDtos.PublicEntourageMember;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.EntourageMemberRepository;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final GuestRepository guestRepository;
    private final ActivityLogService activityLog;

    public EntourageService(EntourageMemberRepository entourageMemberRepository,
                            ProjectRepository projectRepository,
                            GuestRepository guestRepository,
                            ActivityLogService activityLog) {
        this.entourageMemberRepository = entourageMemberRepository;
        this.projectRepository = projectRepository;
        this.guestRepository = guestRepository;
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

    /**
     * Adds one entourage row per guest id, copying that guest's current role name and full name.
     * A guest with no role, or whose role isn't marked {@code entourageEligible}, is skipped
     * ({@code skippedNotEligible}) — the frontend picker already filters these out, so this is a
     * defensive backstop against a crafted request, not the primary UX. A guest whose name
     * (trimmed, case-insensitive) already matches an existing entourage member is skipped too
     * ({@code skippedAlreadyPresent}), so re-running an import is idempotent. A guest id from
     * another project 404s the whole request — same defence-in-depth as every other child
     * resource in this codebase.
     */
    @Transactional
    public ImportFromGuestsResult importFromGuests(UUID projectId, List<UUID> guestIds) {
        Project project = requireProject(projectId);
        List<EntourageMember> existing = orderedMembers(projectId);
        Set<String> existingNames = new HashSet<>();
        for (EntourageMember member : existing) {
            existingNames.add(normalizeName(member.getName()));
        }
        int nextOrder = existing.stream()
                .mapToInt(EntourageMember::getSortOrder)
                .max()
                .orElse(-1) + 1;

        int added = 0;
        int skippedAlreadyPresent = 0;
        int skippedNotEligible = 0;
        for (UUID guestId : guestIds) {
            Guest guest = guestRepository.findById(guestId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Guest", guestId));
            if (!guest.getProject().getId().equals(projectId)) {
                throw ResourceNotFoundException.of("Guest", guestId);
            }
            if (guest.getRole() == null || !guest.getRole().isEntourageEligible()) {
                skippedNotEligible++;
                continue;
            }
            String normalizedName = normalizeName(guest.getFullName());
            if (existingNames.contains(normalizedName)) {
                skippedAlreadyPresent++;
                continue;
            }
            EntourageMember member = new EntourageMember(
                    guest.getRole().getName(), guest.getFullName(), nextOrder++);
            project.addEntourageMember(member);
            entourageMemberRepository.save(member);
            existingNames.add(normalizedName);
            added++;
        }

        if (added > 0) {
            activityLog.record(projectId, ActivityEntityType.ENTOURAGE_MEMBER, projectId,
                    ActivityAction.CREATE, "Imported " + added + " member(s) from the guest list");
        }
        return new ImportFromGuestsResult(added, skippedAlreadyPresent, skippedNotEligible);
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
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

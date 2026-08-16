package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.GuestRequest;
import com.wedding.planner.dto.GuestResponse;
import com.wedding.planner.dto.RsvpDtos.RsvpUpdateRequest;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for guests nested under a project. See {@link TaskService} for the project-scoping pattern
 * that keeps authorization sound.
 */
@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final ProjectRepository projectRepository;
    private final GuestRoleService guestRoleService;
    private final EntourageService entourageService;
    private final ActivityLogService activityLog;

    public GuestService(GuestRepository guestRepository,
                        ProjectRepository projectRepository,
                        GuestRoleService guestRoleService,
                        EntourageService entourageService,
                        ActivityLogService activityLog) {
        this.guestRepository = guestRepository;
        this.projectRepository = projectRepository;
        this.guestRoleService = guestRoleService;
        this.entourageService = entourageService;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> list(UUID projectId) {
        requireProject(projectId);
        return guestRepository.findByProjectIdWithRoles(projectId).stream()
                .map(GuestResponse::from)
                .toList();
    }

    @Transactional
    public GuestResponse create(UUID projectId, GuestRequest request) {
        Project project = requireProject(projectId);
        Guest guest = fromRequest(request);
        guest.setProject(project);
        Guest saved = guestRepository.save(guest);
        activityLog.record(projectId, ActivityEntityType.GUEST, saved.getId(),
                ActivityAction.CREATE, "Added guest \"" + saved.getFullName() + "\"");
        return GuestResponse.from(saved);
    }

    /** Bulk create (CSV import). Validated per row by the controller; all-or-nothing. */
    @Transactional
    public List<GuestResponse> importGuests(UUID projectId, List<GuestRequest> requests) {
        Project project = requireProject(projectId);
        List<Guest> guests = requests.stream()
                .map(request -> {
                    Guest guest = fromRequest(request);
                    guest.setProject(project);
                    return guest;
                })
                .toList();
        List<Guest> saved = guestRepository.saveAll(guests);
        // Batch import → one log row, not one per guest, to avoid feed noise.
        activityLog.record(projectId, ActivityEntityType.GUEST, null,
                ActivityAction.CREATE, "Imported " + saved.size() + " guests");
        return saved.stream().map(GuestResponse::from).toList();
    }

    @Transactional
    public GuestResponse update(UUID projectId, UUID guestId, GuestRequest request) {
        Guest guest = requireGuestInProject(projectId, guestId);
        boolean rsvpChanged = guest.getRsvpStatus() != request.rsvpStatus();
        guest.setFirstName(request.firstName());
        guest.setLastName(request.lastName());
        guest.setTitle(request.title());
        guest.setGender(request.gender());
        guest.setEmail(request.email());
        guest.setPhone(request.phone());
        guest.setRsvpStatus(request.rsvpStatus());
        guest.setPartySize(request.partySize());
        guest.setDietaryNotes(request.dietaryNotes());
        guest.setTableNumber(request.tableNumber());
        guest.setPriority(request.priority());
        guest.setRelatedTo(request.relatedTo());
        guest.setRelationship(request.relationship());
        guest.replaceRoles(guestRoleService.resolveRoles(request.roleIds()));
        String summary = rsvpChanged
                ? "Marked \"" + guest.getFullName() + "\" as " + guest.getRsvpStatus().name()
                : "Updated guest \"" + guest.getFullName() + "\"";
        activityLog.record(projectId, ActivityEntityType.GUEST, guestId,
                ActivityAction.UPDATE, summary);
        return GuestResponse.from(guest);
    }

    private Guest fromRequest(GuestRequest request) {
        Guest guest = new Guest(request.firstName(), request.rsvpStatus(), request.partySize());
        guest.setLastName(request.lastName());
        guest.setTitle(request.title());
        guest.setGender(request.gender());
        guest.setEmail(request.email());
        guest.setPhone(request.phone());
        guest.setDietaryNotes(request.dietaryNotes());
        guest.setTableNumber(request.tableNumber());
        guest.setPriority(request.priority());
        guest.setRelatedTo(request.relatedTo());
        guest.setRelationship(request.relationship());
        guest.replaceRoles(guestRoleService.resolveRoles(request.roleIds()));
        return guest;
    }

    @Transactional
    public void delete(UUID projectId, UUID guestId) {
        Guest guest = requireGuestInProject(projectId, guestId);
        String name = guest.getFullName();
        guest.setDeletedAt(Instant.now());
        activityLog.record(projectId, ActivityEntityType.GUEST, guestId,
                ActivityAction.DELETE, "Deleted guest \"" + name + "\"");
    }

    @Transactional
    public GuestResponse restore(UUID projectId, UUID guestId) {
        if (guestRepository.restore(guestId, projectId) == 0) {
            throw ResourceNotFoundException.of("Guest", guestId);
        }
        Guest guest = requireGuestInProject(projectId, guestId);
        activityLog.record(projectId, ActivityEntityType.GUEST, guestId,
                ActivityAction.RESTORE, "Restored guest \"" + guest.getFullName() + "\"");
        return GuestResponse.from(guest);
    }

    // --- Public RSVP (token-authenticated, no login) ---

    @Transactional(readOnly = true)
    public RsvpViewResponse viewByRsvpToken(UUID token) {
        Guest guest = requireByRsvpToken(token);
        return RsvpViewResponse.from(guest, entourageService.listForPublicView(guest.getProject().getId()));
    }

    /**
     * Resolves the project id behind an RSVP token — used only to stream the project's cover
     * photo ({@code GET /api/public/rsvp/{token}/cover}) without widening the public DTO to
     * expose any id directly.
     */
    @Transactional(readOnly = true)
    public UUID projectIdByRsvpToken(UUID token) {
        return requireByRsvpToken(token).getProject().getId();
    }

    /**
     * Lets the invitee update only their own RSVP fields — never name/table/contact.
     *
     * <p>Party size is only writable here when the project has opted in
     * ({@code allowGuestPartySize}, default off). When it's off, {@code request.partySize()} is
     * ignored outright and the guest's existing value is left as-is — previously this method
     * unconditionally reset party size to 1 on every submission, silently clobbering whatever the
     * planner had set even when the toggle didn't exist yet to opt out of it.
     */
    @Transactional
    public RsvpViewResponse respondByRsvpToken(UUID token, RsvpUpdateRequest request) {
        Guest guest = requireByRsvpToken(token);
        Project project = guest.getProject();
        guest.setRsvpStatus(request.rsvpStatus());
        guest.setDietaryNotes(request.dietaryNotes());
        if (project.isAllowGuestPartySize() && request.partySize() != null) {
            Integer max = project.getMaxPartySize();
            if (max != null && request.partySize() > max) {
                throw new BadRequestException("Party size cannot exceed " + max + ".");
            }
            guest.setPartySize(request.partySize());
        }
        return RsvpViewResponse.from(guest, entourageService.listForPublicView(project.getId()));
    }

    private Guest requireByRsvpToken(UUID token) {
        return guestRepository.findByRsvpToken(token)
                .orElseThrow(() -> ResourceNotFoundException.of("RSVP", token));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private Guest requireGuestInProject(UUID projectId, UUID guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Guest", guestId));
        if (!guest.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Guest", guestId);
        }
        return guest;
    }
}

package com.wedding.planner.service;

import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.GuestRequest;
import com.wedding.planner.dto.GuestResponse;
import com.wedding.planner.dto.RsvpDtos.RsvpUpdateRequest;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.ProjectRepository;
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

    public GuestService(GuestRepository guestRepository, ProjectRepository projectRepository) {
        this.guestRepository = guestRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> list(UUID projectId) {
        requireProject(projectId);
        return guestRepository.findByProjectId(projectId).stream()
                .map(GuestResponse::from)
                .toList();
    }

    @Transactional
    public GuestResponse create(UUID projectId, GuestRequest request) {
        Project project = requireProject(projectId);
        Guest guest = fromRequest(request);
        guest.setProject(project);
        return GuestResponse.from(guestRepository.save(guest));
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
        return guestRepository.saveAll(guests).stream().map(GuestResponse::from).toList();
    }

    @Transactional
    public GuestResponse update(UUID projectId, UUID guestId, GuestRequest request) {
        Guest guest = requireGuestInProject(projectId, guestId);
        guest.setName(request.name());
        guest.setEmail(request.email());
        guest.setPhone(request.phone());
        guest.setRsvpStatus(request.rsvpStatus());
        guest.setPartySize(request.partySize());
        guest.setDietaryNotes(request.dietaryNotes());
        guest.setTableNumber(request.tableNumber());
        return GuestResponse.from(guest);
    }

    private Guest fromRequest(GuestRequest request) {
        Guest guest = new Guest(request.name(), request.rsvpStatus(), request.partySize());
        guest.setEmail(request.email());
        guest.setPhone(request.phone());
        guest.setDietaryNotes(request.dietaryNotes());
        guest.setTableNumber(request.tableNumber());
        return guest;
    }

    @Transactional
    public void delete(UUID projectId, UUID guestId) {
        Guest guest = requireGuestInProject(projectId, guestId);
        guestRepository.delete(guest);
    }

    // --- Public RSVP (token-authenticated, no login) ---

    @Transactional(readOnly = true)
    public RsvpViewResponse viewByRsvpToken(UUID token) {
        return RsvpViewResponse.from(requireByRsvpToken(token));
    }

    /** Lets the invitee update only their own RSVP fields — never name/table/contact. */
    @Transactional
    public RsvpViewResponse respondByRsvpToken(UUID token, RsvpUpdateRequest request) {
        Guest guest = requireByRsvpToken(token);
        guest.setRsvpStatus(request.rsvpStatus());
        guest.setPartySize(request.partySize());
        guest.setDietaryNotes(request.dietaryNotes());
        return RsvpViewResponse.from(guest);
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

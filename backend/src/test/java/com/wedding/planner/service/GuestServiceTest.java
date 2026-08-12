package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.RsvpStatus;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.RsvpDtos.RsvpUpdateRequest;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the public-RSVP party-size branch of {@link GuestService#respondByRsvpToken}.
 * The toggle lives on {@link Project}; these tests cover all three outcomes without needing a
 * database.
 */
@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GuestRoleService guestRoleService;

    @Mock
    private ActivityLogService activityLog;

    @InjectMocks
    private GuestService guestService;

    private Guest guestFor(Project project, int existingPartySize) {
        Guest guest = new Guest("Alex", RsvpStatus.PENDING, existingPartySize);
        guest.setProject(project);
        return guest;
    }

    private Project projectWithToggle(boolean allow, Integer maxPartySize) {
        Project project = new Project("Toggle Wedding", org.mockito.Mockito.mock(User.class));
        project.setAllowGuestPartySize(allow);
        project.setMaxPartySize(maxPartySize);
        return project;
    }

    @Test
    void toggleOffIgnoresRequestedPartySizeAndPreservesExisting() {
        Project project = projectWithToggle(false, null);
        Guest guest = guestFor(project, 2);
        UUID token = guest.getRsvpToken();
        when(guestRepository.findByRsvpToken(token)).thenReturn(Optional.of(guest));

        RsvpViewResponse response = guestService.respondByRsvpToken(
                token, new RsvpUpdateRequest(RsvpStatus.ATTENDING, null, 5));

        assertThat(response.partySize()).isEqualTo(2);
        assertThat(guest.getPartySize()).isEqualTo(2);
    }

    @Test
    void toggleOnAcceptsAValidPartySize() {
        Project project = projectWithToggle(true, 6);
        Guest guest = guestFor(project, 1);
        UUID token = guest.getRsvpToken();
        when(guestRepository.findByRsvpToken(token)).thenReturn(Optional.of(guest));

        RsvpViewResponse response = guestService.respondByRsvpToken(
                token, new RsvpUpdateRequest(RsvpStatus.ATTENDING, null, 4));

        assertThat(response.partySize()).isEqualTo(4);
        assertThat(guest.getPartySize()).isEqualTo(4);
    }

    @Test
    void toggleOnRejectsAPartySizeOverTheCap() {
        Project project = projectWithToggle(true, 3);
        Guest guest = guestFor(project, 1);
        UUID token = guest.getRsvpToken();
        when(guestRepository.findByRsvpToken(token)).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> guestService.respondByRsvpToken(
                token, new RsvpUpdateRequest(RsvpStatus.ATTENDING, null, 4)))
                .isInstanceOf(BadRequestException.class);

        // Rejected before any mutation — the guest's party size is untouched.
        assertThat(guest.getPartySize()).isEqualTo(1);
    }

    @Test
    void toggleOnWithNoRequestedSizeLeavesExistingValueAlone() {
        Project project = projectWithToggle(true, null);
        Guest guest = guestFor(project, 3);
        UUID token = guest.getRsvpToken();
        when(guestRepository.findByRsvpToken(token)).thenReturn(Optional.of(guest));

        RsvpViewResponse response = guestService.respondByRsvpToken(
                token, new RsvpUpdateRequest(RsvpStatus.ATTENDING, null, null));

        assertThat(response.partySize()).isEqualTo(3);
    }
}

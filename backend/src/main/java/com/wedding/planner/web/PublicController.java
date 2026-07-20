package com.wedding.planner.web;

import com.wedding.planner.dto.InvitationDtos.InvitationPublicResponse;
import com.wedding.planner.dto.RsvpDtos.RsvpUpdateRequest;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.service.GuestService;
import com.wedding.planner.service.InvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated endpoints ({@code /api/public/**} is permitted in the security config).
 * Everything here is keyed by an unguessable UUID token, exposes no internal ids, and allows
 * only the narrow updates an invitee should make about themselves.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final GuestService guestService;
    private final InvitationService invitationService;

    public PublicController(GuestService guestService, InvitationService invitationService) {
        this.guestService = guestService;
        this.invitationService = invitationService;
    }

    /** What a guest sees when opening their RSVP link. */
    @GetMapping("/rsvp/{token}")
    public RsvpViewResponse viewRsvp(@PathVariable UUID token) {
        return guestService.viewByRsvpToken(token);
    }

    /** A guest submitting / changing their RSVP. */
    @PutMapping("/rsvp/{token}")
    public RsvpViewResponse respond(@PathVariable UUID token,
                                    @Valid @RequestBody RsvpUpdateRequest request) {
        return guestService.respondByRsvpToken(token, request);
    }

    /** Invitation preview for the register page (prefills the invitee's email). */
    @GetMapping("/invitations/{token}")
    public InvitationPublicResponse invitation(@PathVariable UUID token) {
        return invitationService.findPublic(token);
    }
}

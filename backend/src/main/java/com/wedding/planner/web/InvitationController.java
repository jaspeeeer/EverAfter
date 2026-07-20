package com.wedding.planner.web;

import com.wedding.planner.dto.InvitationDtos.InvitationRequest;
import com.wedding.planner.dto.InvitationDtos.InvitationResponse;
import com.wedding.planner.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Couple invitations, nested under a project. Only whoever can MANAGE the project (admin or the
 * managing planner) may issue or view invites — they contain the secret registration token.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canManage(#projectId, authentication)")
    public List<InvitationResponse> list(@PathVariable UUID projectId) {
        return invitationService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canManage(#projectId, authentication)")
    public ResponseEntity<InvitationResponse> create(@PathVariable UUID projectId,
                                                     @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.create(projectId, request));
    }
}

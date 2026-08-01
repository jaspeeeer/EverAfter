package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Invitation;
import com.wedding.planner.domain.InvitationStatus;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.InvitationDtos.InvitationPublicResponse;
import com.wedding.planner.dto.InvitationDtos.InvitationRequest;
import com.wedding.planner.dto.InvitationDtos.InvitationResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.InvitationRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couple invitations: a planner issues a token-bearing invite for a project; registering with
 * that token attaches the new account as the project's owning couple.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository;
    private final ActivityLogService activityLog;

    public InvitationService(InvitationRepository invitationRepository,
                             ProjectRepository projectRepository,
                             ActivityLogService activityLog) {
        this.invitationRepository = invitationRepository;
        this.projectRepository = projectRepository;
        this.activityLog = activityLog;
    }

    @Transactional
    public InvitationResponse create(UUID projectId, InvitationRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
        if (project.getOwner() != null) {
            throw new BadRequestException("This project already has an owning couple");
        }
        Invitation invitation = new Invitation(request.email().trim().toLowerCase(), project);
        Invitation saved = invitationRepository.save(invitation);
        activityLog.record(projectId, ActivityEntityType.INVITATION, saved.getId(),
                ActivityAction.CREATE, "Invited couple " + saved.getEmail());
        return InvitationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw ResourceNotFoundException.of("Project", projectId);
        }
        return invitationRepository.findByProjectId(projectId).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    /** Public lookup used by the register page to prefill the invitee's email. */
    @Transactional(readOnly = true)
    public InvitationPublicResponse findPublic(UUID token) {
        return InvitationPublicResponse.from(requireByToken(token));
    }

    /**
     * Consumes a pending invitation: attaches {@code couple} as the project owner. Called from
     * registration, inside the same transaction.
     */
    @Transactional
    public void accept(UUID token, User couple) {
        Invitation invitation = requireByToken(token);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("This invitation has already been used");
        }
        Project project = invitation.getProject();
        if (project.getOwner() != null) {
            throw new BadRequestException("This project already has an owning couple");
        }
        project.setOwner(couple);
        invitation.accept();
        activityLog.record(project.getId(), ActivityEntityType.PROJECT, project.getId(),
                ActivityAction.UPDATE, "Couple " + couple.getEmail() + " accepted planner invitation");
    }

    private Invitation requireByToken(UUID token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> ResourceNotFoundException.of("Invitation", token));
    }
}

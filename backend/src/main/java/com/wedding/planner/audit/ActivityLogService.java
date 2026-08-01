package com.wedding.planner.audit;

import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.ActivityLog;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.ActivityLogResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ActivityLogRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.security.AppUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records project-scoped mutations. Writes participate in the caller's transaction (default
 * REQUIRED propagation) so a CREATE log entry can reference the just-inserted row. Log-row inserts
 * against activity_log have no unique constraints and no interesting FKs beyond project + actor,
 * so a failure here is a symptom of a deeper problem and rolling back the mutation is preferable
 * to a silently-missing audit trail.
 */
@Service
public class ActivityLogService {

    private static final int MAX_PAGE = 100;
    private static final int DEFAULT_PAGE = 25;

    private final ActivityLogRepository logRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ActivityLogService(ActivityLogRepository logRepository,
                              ProjectRepository projectRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider) {
        this.logRepository = logRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Records one mutation. Uses a JPA lazy reference for {@code project} so we don't SELECT
     * when the caller has just INSERTed the row in the same transaction — the FK is validated at
     * flush time by Postgres, which happily sees the row from the same session.
     */
    @Transactional
    public void record(UUID projectId,
                       ActivityEntityType entityType,
                       UUID entityId,
                       ActivityAction action,
                       String summary) {
        Project projectRef = projectRepository.getReferenceById(projectId);
        Optional<AppUserPrincipal> principal = currentUserProvider.currentPrincipal();
        User actorRef = principal.map(p -> userRepository.getReferenceById(p.getId())).orElse(null);
        String actorEmail = principal.map(AppUserPrincipal::getUsername).orElse(null);
        ActivityLog row = new ActivityLog(
                projectRef, actorRef, actorEmail, entityType, entityId, action, summary);
        logRepository.save(row);
    }

    /** Cursor-paginated feed. Callers must have already passed {@code @projectSecurity.canAccess}. */
    @Transactional(readOnly = true)
    public List<ActivityLogResponse> feed(UUID projectId,
                                          ActivityEntityType entityType,
                                          UUID actorId,
                                          Instant cursorCreatedAt,
                                          UUID cursorId,
                                          Integer limit) {
        if (projectRepository.findById(projectId).isEmpty()) {
            throw ResourceNotFoundException.of("Project", projectId);
        }
        int size = clampLimit(limit);
        return logRepository.findPage(projectId, entityType, actorId,
                        cursorCreatedAt, cursorId, PageRequest.of(0, size))
                .stream().map(ActivityLogResponse::from).toList();
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_PAGE;
        return Math.min(limit, MAX_PAGE);
    }
}

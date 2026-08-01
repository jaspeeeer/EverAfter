package com.wedding.planner.notification;

import com.wedding.planner.domain.Notification;
import com.wedding.planner.domain.NotificationPreferences;
import com.wedding.planner.domain.NotificationType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.NotificationPreferencesDtos.NotificationPreferencesRequest;
import com.wedding.planner.dto.NotificationPreferencesDtos.NotificationPreferencesResponse;
import com.wedding.planner.dto.NotificationResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.NotificationPreferencesRepository;
import com.wedding.planner.repository.NotificationRepository;
import com.wedding.planner.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write API for in-app notifications. Deduplication is enforced by the partial-unique index
 * {@code uq_notifications_user_dedupe} via a native {@code INSERT ... ON CONFLICT DO NOTHING}
 * so a duplicate never throws — the enclosing transaction stays healthy.
 */
@Service
public class NotificationService {

    private static final int MAX_LIST_LIMIT = 100;
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferencesRepository preferencesRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
    }

    /**
     * Persist a notification row. A non-null {@code dedupeKey} makes the write idempotent per user
     * — a same-key retry becomes a no-op and returns {@code false}. REQUIRES_NEW so a per-recipient
     * write is isolated from the caller's outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enqueue(User user,
                           NotificationType type,
                           String title,
                           String body,
                           String linkPath,
                           String entityType,
                           UUID entityId,
                           Project project,
                           String dedupeKey) {
        int inserted = notificationRepository.insertIdempotent(
                UUID.randomUUID(),
                user.getId(),
                project != null ? project.getId() : null,
                type.name(),
                title,
                body,
                linkPath,
                entityType,
                entityId,
                dedupeKey);
        return inserted > 0;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId, boolean unreadOnly, Integer limit) {
        int size = clampLimit(limit);
        Pageable page = PageRequest.of(0, size);
        List<Notification> rows = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, page)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, page);
        return rows.stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        // Never leak existence across users — treat cross-user access as 404.
        if (!n.getUser().getId().equals(userId)) {
            throw ResourceNotFoundException.of("Notification", notificationId);
        }
        n.markRead();
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        return NotificationPreferencesResponse.from(getOrCreatePreferences(userId));
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId,
                                                             NotificationPreferencesRequest req) {
        NotificationPreferences prefs = getOrCreatePreferences(userId);
        prefs.setInappTaskDue(req.inappTaskDue());
        prefs.setInappPaymentDue(req.inappPaymentDue());
        prefs.setInappCountdown(req.inappCountdown());
        return NotificationPreferencesResponse.from(prefs);
    }

    /** Loads or lazily creates the preferences row for a user. Callers must be transactional. */
    public NotificationPreferences getOrCreatePreferences(UUID userId) {
        return preferencesRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
            return preferencesRepository.save(new NotificationPreferences(user));
        });
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIST_LIMIT;
        return Math.min(limit, MAX_LIST_LIMIT);
    }
}

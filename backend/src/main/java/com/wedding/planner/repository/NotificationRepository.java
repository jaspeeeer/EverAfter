package com.wedding.planner.repository;

import com.wedding.planner.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt " +
           "where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    /**
     * Idempotent insert: on collision with the {@code uq_notifications_user_dedupe} partial-unique
     * index the row is silently skipped. Native SQL avoids the exception path in JPA, which would
     * poison the enclosing transaction. Returns the number of rows written (0 or 1).
     */
    @Modifying
    @Query(value = "INSERT INTO notifications (id, user_id, project_id, type, title, body, " +
                   "link_path, entity_type, entity_id, dedupe_key, read_at, created_at) " +
                   "VALUES (:id, :userId, :projectId, :type, :title, :body, " +
                   ":linkPath, :entityType, :entityId, :dedupeKey, NULL, now()) " +
                   "ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING",
           nativeQuery = true)
    int insertIdempotent(@Param("id") UUID id,
                         @Param("userId") UUID userId,
                         @Param("projectId") UUID projectId,
                         @Param("type") String type,
                         @Param("title") String title,
                         @Param("body") String body,
                         @Param("linkPath") String linkPath,
                         @Param("entityType") String entityType,
                         @Param("entityId") UUID entityId,
                         @Param("dedupeKey") String dedupeKey);
}

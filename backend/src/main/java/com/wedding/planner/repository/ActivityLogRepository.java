package com.wedding.planner.repository;

import com.wedding.planner.domain.ActivityLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    /**
     * Cursor-paginated feed for a project. When {@code cursorCreatedAt} is null, returns the newest
     * page; otherwise returns rows strictly older than the cursor. Nullable enum/UUID params are
     * cast so Postgres can bind them ({@code ? is null} with an untyped placeholder is rejected).
     */
    @Query("select a from ActivityLog a where a.project.id = :projectId "
            + "and (cast(:entityType as string) is null or a.entityType = :entityType) "
            + "and (cast(:actorId as string) is null or a.actor.id = :actorId) "
            + "and (cast(:cursorCreatedAt as timestamp) is null "
            + "     or a.createdAt < :cursorCreatedAt "
            + "     or (a.createdAt = :cursorCreatedAt and a.id < :cursorId)) "
            + "order by a.createdAt desc, a.id desc")
    List<ActivityLog> findPage(@Param("projectId") UUID projectId,
                               @Param("entityType") com.wedding.planner.domain.ActivityEntityType entityType,
                               @Param("actorId") UUID actorId,
                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                               @Param("cursorId") UUID cursorId,
                               Pageable pageable);
}

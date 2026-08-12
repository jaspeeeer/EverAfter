package com.wedding.planner.repository;

import com.wedding.planner.domain.Guest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {

    List<Guest> findByProjectId(UUID projectId);

    Optional<Guest> findByRsvpToken(UUID rsvpToken);

    /** Whether any guest references a role — used to gate role deletion. */
    long countByRoleId(UUID roleId);

    /**
     * Restores a soft-deleted guest, scoped to its project; returns the row count (0 = not found
     * / wrong project / not deleted). Native, so it bypasses {@code @SQLRestriction} — the normal
     * {@code findById} can't see a tombstoned row to restore it.
     */
    @Modifying
    @Query(value = "update guests set deleted_at = null where id = :id and project_id = :projectId "
            + "and deleted_at is not null", nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("projectId") UUID projectId);
}

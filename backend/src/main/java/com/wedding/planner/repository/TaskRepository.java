package com.wedding.planner.repository;

import com.wedding.planner.domain.Task;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectId(UUID projectId);

    /**
     * Tasks whose due date is one of the given dates and which are not already done. Used by the
     * reminder scheduler to find "task due in N days" candidates in one query per horizon set.
     */
    List<Task> findByDueDateInAndStatusNot(Collection<LocalDate> dueDates,
                                           com.wedding.planner.domain.TaskStatus excludedStatus);

    /**
     * Restores a soft-deleted task, scoped to its project; returns the row count (0 = not found /
     * wrong project / not deleted). Native, so it bypasses {@code @SQLRestriction} — the normal
     * {@code findById} can't see a tombstoned row to restore it.
     */
    @Modifying
    @Query(value = "update tasks set deleted_at = null where id = :id and project_id = :projectId "
            + "and deleted_at is not null", nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("projectId") UUID projectId);
}

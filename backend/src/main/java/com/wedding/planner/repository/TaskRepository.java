package com.wedding.planner.repository;

import com.wedding.planner.domain.Task;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

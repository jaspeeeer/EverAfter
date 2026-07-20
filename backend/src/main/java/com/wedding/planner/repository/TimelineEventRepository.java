package com.wedding.planner.repository;

import com.wedding.planner.domain.TimelineEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {

    /** Eagerly fetches vendors so responses map outside the persistence context. */
    @Query("select distinct e from TimelineEvent e left join fetch e.vendors "
            + "where e.project.id = :projectId")
    List<TimelineEvent> findByProjectIdWithVendors(@Param("projectId") UUID projectId);

    @Query("select e from TimelineEvent e left join fetch e.vendors where e.id = :id")
    Optional<TimelineEvent> findByIdWithVendors(@Param("id") UUID id);

    long countByProjectId(UUID projectId);
}

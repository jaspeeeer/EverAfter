package com.wedding.planner.repository;

import com.wedding.planner.domain.Project;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** All projects managed by a given planner — the seam for the planner isolation rule. */
    List<Project> findByPlannerId(UUID plannerId);

    /** The single project owned by a given couple/user, if any. */
    Optional<Project> findByOwnerId(UUID ownerId);

    /** Projects whose wedding_date is one of the given dates — used by the countdown reminder. */
    List<Project> findByWeddingDateIn(Collection<LocalDate> weddingDates);
}

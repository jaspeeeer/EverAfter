package com.wedding.planner.repository;

import com.wedding.planner.domain.Guest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {

    List<Guest> findByProjectId(UUID projectId);

    Optional<Guest> findByRsvpToken(UUID rsvpToken);

    /** Whether any guest references a role — used to gate role deletion. */
    long countByRoleId(UUID roleId);
}

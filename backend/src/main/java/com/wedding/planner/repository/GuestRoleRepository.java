package com.wedding.planner.repository;

import com.wedding.planner.domain.GuestRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuestRoleRepository extends JpaRepository<GuestRole, UUID> {

    List<GuestRole> findAllByOrderBySortOrderAsc();

    List<GuestRole> findByActiveTrueOrderBySortOrderAsc();

    Optional<GuestRole> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    long countByParentId(UUID parentId);

    List<GuestRole> findByParentId(UUID parentId);
}

package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorCategoryRepository extends JpaRepository<VendorCategory, UUID> {

    List<VendorCategory> findAllByOrderBySortOrderAsc();

    List<VendorCategory> findByActiveTrueOrderBySortOrderAsc();

    Optional<VendorCategory> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}

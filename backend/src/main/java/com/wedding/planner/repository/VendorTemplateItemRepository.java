package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorTemplateItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorTemplateItemRepository extends JpaRepository<VendorTemplateItem, UUID> {

    /** Used to decide whether a category can be hard-deleted or must be deactivated. */
    long countByCategoryId(UUID categoryId);
}

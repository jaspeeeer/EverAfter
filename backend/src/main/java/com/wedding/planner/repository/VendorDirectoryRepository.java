package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorDirectoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorDirectoryRepository extends JpaRepository<VendorDirectoryEntry, UUID> {

    @Query("select d from VendorDirectoryEntry d join fetch d.category order by d.name")
    List<VendorDirectoryEntry> findAllWithCategory();

    @Query("select d from VendorDirectoryEntry d join fetch d.category "
            + "where d.active = true order by d.name")
    List<VendorDirectoryEntry> findActiveWithCategory();

    long countByCategoryId(UUID categoryId);
}

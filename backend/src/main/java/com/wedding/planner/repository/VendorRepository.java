package com.wedding.planner.repository;

import com.wedding.planner.domain.Vendor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    List<Vendor> findByProjectId(UUID projectId);
}

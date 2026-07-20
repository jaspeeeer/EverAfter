package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorTemplateRepository extends JpaRepository<VendorTemplate, UUID> {

    /** Eagerly fetches items so responses can be mapped outside the persistence context. */
    @Query("select distinct t from VendorTemplate t left join fetch t.items order by t.name")
    List<VendorTemplate> findAllWithItems();

    @Query("select t from VendorTemplate t left join fetch t.items where t.id = :id")
    Optional<VendorTemplate> findByIdWithItems(@Param("id") UUID id);
}

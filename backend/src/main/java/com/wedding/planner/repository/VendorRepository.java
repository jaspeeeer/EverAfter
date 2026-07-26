package com.wedding.planner.repository;

import com.wedding.planner.domain.Vendor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    List<Vendor> findByProjectId(UUID projectId);

    @Query("select v from Vendor v join fetch v.category where v.project.id = :projectId")
    List<Vendor> findByProjectIdWithCategory(@Param("projectId") UUID projectId);

    long countByCategoryId(UUID categoryId);

    long countByDirectoryEntryId(UUID directoryEntryId);

    /** Top-level vendors (packages and standalone vendors) — excludes nested package items. */
    long countByParentIsNull();

    /** How many items (children) a vendor has — used to stop a package from becoming an item. */
    long countByParentId(UUID parentId);

    // --- Admin report aggregations (rows mapped in ReportService) ---
    // All three exclude package items (v.parent is not null): a package's price/booking is
    // already counted once via the package itself, so counting its items too would double it.

    /** Rows of {@code [categoryId, categoryName, vendorCount, bookedCount, totalAgreedValue]}. */
    @Query("""
            select c.id, c.name, count(v),
                   sum(case when v.booked = true then 1 else 0 end),
                   coalesce(sum(v.agreedPrice), 0)
            from Vendor v join v.category c
            where v.parent is null
            group by c.id, c.name
            order by count(v) desc, c.name asc
            """)
    List<Object[]> vendorCountsByCategory();

    /**
     * Rows of {@code [vendorName, categoryName, usageCount, bookedCount, totalAgreedValue,
     * fromDirectory]}, grouped by the directory entry's name when linked, else the vendor's name,
     * restricted to projects whose wedding date falls in an optional [from, to] window and an
     * optional category.
     */
    // Boolean guards (rather than ":from is null") keep each optional parameter inside a typed
    // comparison, so Postgres can infer the parameter types even when the value is null.
    @Query("""
            select coalesce(d.name, v.name), c.name, count(v),
                   sum(case when v.booked = true then 1 else 0 end),
                   coalesce(sum(v.agreedPrice), 0),
                   case when d.id is not null then true else false end
            from Vendor v
                 join v.category c
                 join v.project p
                 left join v.directoryEntry d
            where v.parent is null
              and (:hasFrom = false or p.weddingDate >= :from)
              and (:hasTo = false or p.weddingDate <= :to)
              and (:hasCategory = false or c.id = :categoryId)
            group by coalesce(d.name, v.name), c.name,
                     case when d.id is not null then true else false end
            order by count(v) desc, coalesce(d.name, v.name) asc
            """)
    List<Object[]> inDemandVendors(@Param("hasFrom") boolean hasFrom,
                                   @Param("from") LocalDate from,
                                   @Param("hasTo") boolean hasTo,
                                   @Param("to") LocalDate to,
                                   @Param("hasCategory") boolean hasCategory,
                                   @Param("categoryId") UUID categoryId);

    /** Rows of {@code [categoryName, considered, booked]} for the booking-conversion report. */
    @Query("""
            select c.name, count(v), sum(case when v.booked = true then 1 else 0 end)
            from Vendor v join v.category c
            where v.parent is null
            group by c.name
            order by c.name asc
            """)
    List<Object[]> bookingConversionByCategory();
}

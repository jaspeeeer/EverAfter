package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorPaymentRepository extends JpaRepository<VendorPayment, UUID> {

    List<VendorPayment> findByVendorIdOrderByPaidOnAscIdAsc(UUID vendorId);

    @Query("select coalesce(sum(p.amount), 0) from VendorPayment p where p.vendor.id = :vendorId")
    BigDecimal sumByVendorId(@Param("vendorId") UUID vendorId);

    /** Per-vendor payment totals for a project (vendorId, sum) — avoids an N+1 in vendor listing. */
    @Query("select p.vendor.id, coalesce(sum(p.amount), 0) from VendorPayment p "
            + "where p.vendor.project.id = :projectId group by p.vendor.id")
    List<Object[]> sumByProjectGroupedByVendor(@Param("projectId") UUID projectId);
}

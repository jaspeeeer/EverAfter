package com.wedding.planner.repository;

import com.wedding.planner.domain.VendorPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorPaymentRepository extends JpaRepository<VendorPayment, UUID> {

    /**
     * Chronological list. Planned rows (paid_on null) sort last via NULLS LAST — the ORM emits
     * that ordering because we spell it out explicitly here.
     */
    @Query("select p from VendorPayment p where p.vendor.id = :vendorId "
            + "order by case when p.paidOn is null then 1 else 0 end, p.paidOn asc, p.dueDate asc, p.id asc")
    List<VendorPayment> findByVendorIdChronological(@Param("vendorId") UUID vendorId);

    /** Sum of PAID amounts only — planned rows do not count against the budget line. */
    @Query("select coalesce(sum(p.amount), 0) from VendorPayment p "
            + "where p.vendor.id = :vendorId and p.paid = true")
    BigDecimal sumPaidByVendorId(@Param("vendorId") UUID vendorId);

    /** Per-vendor PAID payment totals for a project (vendorId, sum) — avoids N+1 in vendor listing. */
    @Query("select p.vendor.id, coalesce(sum(p.amount), 0) from VendorPayment p "
            + "where p.vendor.project.id = :projectId and p.paid = true group by p.vendor.id")
    List<Object[]> sumPaidByProjectGroupedByVendor(@Param("projectId") UUID projectId);

    /**
     * Planned (unpaid) installments whose due date falls in a given horizon set. Used by the
     * reminder scheduler; joins the vendor + project + planner/owner to compute recipients.
     */
    @Query("select p from VendorPayment p "
            + "where p.paid = false and p.dueDate in :dueDates")
    List<VendorPayment> findPlannedByDueDateIn(@Param("dueDates") Collection<LocalDate> dueDates);
}

package com.wedding.planner.repository;

import com.wedding.planner.domain.Expense;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByProjectId(UUID projectId);

    /** The single system-owned budget line that mirrors a vendor's agreed price, if any. */
    Optional<Expense> findByVendorIdAndManagedTrue(UUID vendorId);

    /** Every expense (managed or manually mapped) currently linked to a vendor. */
    List<Expense> findByVendorId(UUID vendorId);

    /** Whether any expense references a category — used to gate category deletion. */
    long countByCategoryId(UUID categoryId);

    /**
     * Restores a soft-deleted expense, scoped to its project; returns the row count (0 = not
     * found / wrong project / not deleted). Native, so it bypasses {@code @SQLRestriction} — the
     * normal {@code findById} can't see a tombstoned row to restore it. Managed lines are never
     * soft-deleted (their own {@code delete()} rejects them before reaching that code), so this
     * never needs to special-case them.
     */
    @Modifying
    @Query(value = "update expenses set deleted_at = null where id = :id and project_id = :projectId "
            + "and deleted_at is not null", nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("projectId") UUID projectId);
}

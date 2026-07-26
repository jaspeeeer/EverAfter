package com.wedding.planner.repository;

import com.wedding.planner.domain.Expense;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByProjectId(UUID projectId);

    /** The single system-owned budget line that mirrors a vendor's agreed price, if any. */
    Optional<Expense> findByVendorIdAndManagedTrue(UUID vendorId);

    /** Whether any expense references a category — used to gate category deletion. */
    long countByCategoryId(UUID categoryId);
}

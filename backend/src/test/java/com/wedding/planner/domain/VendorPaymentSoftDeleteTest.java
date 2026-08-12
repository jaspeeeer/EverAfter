package com.wedding.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedding.planner.AbstractPostgresContainerTest;
import com.wedding.planner.repository.VendorCategoryRepository;
import com.wedding.planner.repository.VendorPaymentRepository;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * A soft-deleted vendor has no {@code deleted_at} column of its own on {@code vendor_payments} —
 * {@link VendorPaymentRepository}'s four queries reach it through an explicit
 * {@code p.vendor.deletedAt is null} predicate rather than relying on {@code @SQLRestriction} to
 * propagate through the implicit association join. This verifies that predicate actually excludes
 * a soft-deleted vendor's payments from every sum/listing — otherwise a "deleted" vendor would
 * keep inflating the budget's paid total.
 */
class VendorPaymentSoftDeleteTest extends AbstractPostgresContainerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private VendorPaymentRepository paymentRepository;

    @Autowired
    private VendorCategoryRepository vendorCategoryRepository;

    private VendorCategory category(String slug) {
        return vendorCategoryRepository.findBySlug(slug).orElseThrow();
    }

    private Project persistProject(String name) {
        User planner = em.persistAndFlush(new User(name + "-planner@wedding.test", "hash", "P", "L"));
        return em.persistAndFlush(new Project(name, planner));
    }

    @Test
    void softDeletedVendorsPaymentsAreExcludedFromEverySum() {
        Project project = persistProject("Soft Deleted Vendor Wedding");
        Vendor vendor = new Vendor("Fading Florist", category("FLORIST"));
        vendor.setProject(project);
        vendor.setAgreedPrice(new BigDecimal("5000.00"));
        vendor = em.persistAndFlush(vendor);

        VendorPayment paid = VendorPayment.recorded(
                vendor, new BigDecimal("2000.00"), LocalDate.now(), "Deposit");
        em.persistAndFlush(paid);
        em.clear();

        // Sanity check: while live, the vendor's payment counts everywhere.
        assertThat(paymentRepository.sumPaidByVendorId(vendor.getId()))
                .isEqualByComparingTo("2000.00");
        assertThat(paymentRepository.findByVendorIdChronological(vendor.getId())).hasSize(1);
        assertThat(paymentRepository.sumPaidByProjectGroupedByVendor(project.getId())).hasSize(1);

        Vendor reloaded = em.find(Vendor.class, vendor.getId());
        reloaded.setDeletedAt(Instant.now());
        em.persistAndFlush(reloaded);
        em.clear();

        assertThat(paymentRepository.sumPaidByVendorId(vendor.getId()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(paymentRepository.findByVendorIdChronological(vendor.getId())).isEmpty();
        assertThat(paymentRepository.sumPaidByProjectGroupedByVendor(project.getId())).isEmpty();
    }
}

package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A single installment against a vendor's agreed price. May be already paid ({@code paid = true},
 * {@code paidOn} set) or planned ({@code paid = false}, {@code dueDate} set, {@code paidOn} null).
 * The DB check constraint {@code chk_vendor_payment_state} enforces this invariant.
 */
@Entity
@Table(name = "vendor_payments")
public class VendorPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vendor_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vendor_payments_vendor")
    )
    private Vendor vendor;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Null when the installment is still planned. */
    @Column(name = "paid_on")
    private LocalDate paidOn;

    /** Required when planned; may be null once the payment has been recorded. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "note", length = 255)
    private String note;

    protected VendorPayment() {
        // Required by JPA.
    }

    /** Factory: a payment already recorded on {@code paidOn}. */
    public static VendorPayment recorded(Vendor vendor, BigDecimal amount, LocalDate paidOn, String note) {
        VendorPayment p = new VendorPayment();
        p.vendor = vendor;
        p.amount = amount;
        p.paidOn = paidOn;
        p.paid = true;
        p.note = note;
        return p;
    }

    /** Factory: a planned installment with a due date. */
    public static VendorPayment planned(Vendor vendor, BigDecimal amount, LocalDate dueDate, String note) {
        VendorPayment p = new VendorPayment();
        p.vendor = vendor;
        p.amount = amount;
        p.dueDate = dueDate;
        p.paid = false;
        p.note = note;
        return p;
    }

    /** Flip a planned installment to paid on the given date. */
    public void markPaid(LocalDate paidOn) {
        this.paid = true;
        this.paidOn = paidOn;
    }

    public UUID getId() {
        return id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public void setPaidOn(LocalDate paidOn) {
        this.paidOn = paidOn;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isPaid() {
        return paid;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VendorPayment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}

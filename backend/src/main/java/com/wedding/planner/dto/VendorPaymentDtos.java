package com.wedding.planner.dto;

import com.wedding.planner.domain.VendorPayment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs for vendor payments (installments). */
public final class VendorPaymentDtos {

    private VendorPaymentDtos() {
    }

    /**
     * Add a payment or planned installment. Either {@code paidOn} (a recorded payment) or
     * {@code dueDate} + {@code paid = false} (a planned installment) must be provided.
     * Server-side validation in {@code VendorService.addPayment} enforces this — leaving both
     * out returns 400.
     */
    public record VendorPaymentRequest(
            @NotNull @Positive BigDecimal amount,
            LocalDate paidOn,
            LocalDate dueDate,
            Boolean paid,
            String note) {

        /** Convenience: a payment is treated as paid unless the caller explicitly sets paid=false. */
        public boolean isPaid() {
            return paid == null || paid;
        }
    }

    /** Body for marking a planned installment as paid. */
    public record MarkPaymentPaidRequest(@NotNull LocalDate paidOn) {}

    public record VendorPaymentResponse(
            UUID id,
            BigDecimal amount,
            LocalDate paidOn,
            LocalDate dueDate,
            boolean paid,
            String note) {

        public static VendorPaymentResponse from(VendorPayment payment) {
            return new VendorPaymentResponse(
                    payment.getId(),
                    payment.getAmount(),
                    payment.getPaidOn(),
                    payment.getDueDate(),
                    payment.isPaid(),
                    payment.getNote());
        }
    }
}

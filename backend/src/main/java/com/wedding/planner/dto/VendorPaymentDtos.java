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

    public record VendorPaymentRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate paidOn,
            String note) {
    }

    public record VendorPaymentResponse(
            UUID id,
            BigDecimal amount,
            LocalDate paidOn,
            String note) {

        public static VendorPaymentResponse from(VendorPayment payment) {
            return new VendorPaymentResponse(
                    payment.getId(), payment.getAmount(), payment.getPaidOn(), payment.getNote());
        }
    }
}

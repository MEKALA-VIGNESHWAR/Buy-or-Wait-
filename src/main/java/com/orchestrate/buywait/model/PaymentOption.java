package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps strictly to dataset/request_payment_options.csv.
 * Represents seller or provider financing offers for a specific request.
 */
public record PaymentOption(
        String paymentOptionId,
        String requestId,
        PaymentMethod paymentMethod,
        BigDecimal paymentAmount,
        Integer numberOfPayments,
        LocalDate firstPaymentDate,
        Integer paymentFrequencyDays,
        BigDecimal financingFee,
        BigDecimal totalPayableAmount
) {
    public boolean isFullPayment() {
        return paymentMethod == PaymentMethod.full_payment;
    }

    public boolean isInstallments() {
        return paymentMethod == PaymentMethod.installments;
    }
}

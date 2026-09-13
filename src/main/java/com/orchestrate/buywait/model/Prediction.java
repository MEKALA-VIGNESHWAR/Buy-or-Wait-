package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps directly to dataset/output.csv (and root output.csv).
 * Contains the required 8 columns in exact specification order.
 */
public record Prediction(
        String requestId,
        BigDecimal amountSafeToPay,
        AffordabilityStatus affordabilityStatus,
        PaymentMethod recommendedPaymentMethod,
        List<Payment> paymentPlan,
        LocalDate earliestDateForFullPayment,
        List<SpendingChange> spendingChangesNeeded,
        String decisionExplanation
) {
    public String formattedPaymentPlan() {
        if (paymentPlan == null || paymentPlan.isEmpty() || recommendedPaymentMethod == PaymentMethod.not_recommended) {
            return "none";
        }
        return paymentPlan.stream()
                .map(Payment::toPlanEntry)
                .collect(Collectors.joining("|"));
    }

    public String formattedSpendingChanges() {
        if (spendingChangesNeeded == null || spendingChangesNeeded.isEmpty()) {
            return "none";
        }
        return spendingChangesNeeded.stream()
                .map(SpendingChange::toOutputFormat)
                .collect(Collectors.joining("|"));
    }

    public String formattedAmountSafeToPay() {
        if (amountSafeToPay == null) {
            return "0";
        }
        return amountSafeToPay.stripTrailingZeros().toPlainString();
    }

    public String formattedEarliestDate() {
        return earliestDateForFullPayment != null ? earliestDateForFullPayment.toString() : "";
    }
}

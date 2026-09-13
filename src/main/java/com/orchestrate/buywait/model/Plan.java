package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a candidate or selected payment plan for a request.
 */
public record Plan(
        PaymentMethod paymentMethod,
        List<Payment> payments,
        BigDecimal totalAmount,
        LocalDate completionDate,
        int paymentCount,
        String paymentOptionId,
        List<SpendingChange> spendingChanges
) {
    public Plan {
        payments = payments != null ? Collections.unmodifiableList(payments) : Collections.emptyList();
        spendingChanges = spendingChanges != null ? Collections.unmodifiableList(spendingChanges) : Collections.emptyList();
        totalAmount = totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public static Plan notRecommended() {
        return new Plan(
                PaymentMethod.not_recommended,
                Collections.emptyList(),
                BigDecimal.ZERO,
                null,
                0,
                null,
                Collections.emptyList()
        );
    }

    public boolean isSafePlan() {
        return paymentMethod != PaymentMethod.not_recommended;
    }

    public boolean requiresSpendingChanges() {
        return !spendingChanges.isEmpty();
    }

    public LocalDate getStartDate() {
        return payments.isEmpty() ? null : payments.get(0).paymentDate();
    }

    public String toPlanString() {
        if (payments.isEmpty() || paymentMethod == PaymentMethod.not_recommended) {
            return "none";
        }
        return payments.stream()
                .map(Payment::toPlanEntry)
                .collect(Collectors.joining("|"));
    }

    public String toSpendingChangesString() {
        if (spendingChanges.isEmpty()) {
            return "none";
        }
        return spendingChanges.stream()
                .map(SpendingChange::toOutputFormat)
                .collect(Collectors.joining("|"));
    }
}

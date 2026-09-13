package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps strictly to dataset/financial_events.csv.
 * Represents historical, pending, scheduled, settled, failed, cancelled, or non-cash financial transactions.
 */
public record FinancialEvent(
        String eventId,
        String userId,
        String eventType,
        String description,
        String category,
        EventDirection direction,
        BigDecimal amount,
        String currency,
        LocalDate eventDate,
        LocalDate settlementDate,
        EventStatus status,
        String linkedEventId,
        EventFlexibility flexibility,
        BigDecimal minimumAllowedAmount
) {
    public boolean isDebit() {
        return direction == EventDirection.debit;
    }

    public boolean isCredit() {
        return direction == EventDirection.credit;
    }

    public boolean isNonCash() {
        return direction == EventDirection.non_cash;
    }

    public boolean isSettled() {
        return status == EventStatus.settled;
    }

    public boolean isPending() {
        return status == EventStatus.pending;
    }

    public boolean isScheduled() {
        return status == EventStatus.scheduled;
    }

    public boolean isCancelledOrFailed() {
        return status == EventStatus.cancelled || status == EventStatus.failed;
    }

    public boolean hasBlankAmount() {
        return amount == null;
    }

    public FinancialEvent withAmount(BigDecimal newAmount) {
        return withAmountAndCurrency(newAmount, this.currency);
    }

    public FinancialEvent withAmountAndCurrency(BigDecimal newAmount, String newCurrency) {
        return new FinancialEvent(
                eventId,
                userId,
                eventType,
                description,
                category,
                direction,
                newAmount,
                newCurrency,
                eventDate,
                settlementDate,
                status,
                linkedEventId,
                flexibility,
                minimumAllowedAmount
        );
    }
}

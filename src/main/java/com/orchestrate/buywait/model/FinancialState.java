package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Reconstructed, normalized financial state of a user on request_date.
 * Strictly separates:
 * - Starting available balance & minimum reserve buffer
 * - Active pending debits (which must be reserved)
 * - Confirmed future income (e.g. salary on settlement date)
 * - Essential future expenses (fixed commitments, protected categories)
 * - Flexible future expenses (eligible for stopping or reducing)
 * - Historical settled transactions
 * - Ignored non-cash / cancelled / failed / duplicate events
 */
public record FinancialState(
        FinancialProfile profile,
        Request request,
        BigDecimal startingBalance,
        BigDecimal minimumBalanceToKeep,
        BigDecimal reservedPendingDebits,
        List<FinancialEvent> pendingDebits,
        List<FinancialEvent> confirmedIncomeEvents,
        List<FinancialEvent> essentialExpenses,
        List<FinancialEvent> flexibleExpenses,
        List<FinancialEvent> historicalEvents,
        List<FinancialEvent> ignoredEvents,
        List<PaymentOption> availablePaymentOptions,
        List<Message> relevantMessages,
        List<ImageReference> relevantImages
) {
    public FinancialState {
        pendingDebits = pendingDebits != null ? Collections.unmodifiableList(pendingDebits) : Collections.emptyList();
        confirmedIncomeEvents = confirmedIncomeEvents != null ? Collections.unmodifiableList(confirmedIncomeEvents) : Collections.emptyList();
        essentialExpenses = essentialExpenses != null ? Collections.unmodifiableList(essentialExpenses) : Collections.emptyList();
        flexibleExpenses = flexibleExpenses != null ? Collections.unmodifiableList(flexibleExpenses) : Collections.emptyList();
        historicalEvents = historicalEvents != null ? Collections.unmodifiableList(historicalEvents) : Collections.emptyList();
        ignoredEvents = ignoredEvents != null ? Collections.unmodifiableList(ignoredEvents) : Collections.emptyList();
        availablePaymentOptions = availablePaymentOptions != null ? Collections.unmodifiableList(availablePaymentOptions) : Collections.emptyList();
        relevantMessages = relevantMessages != null ? Collections.unmodifiableList(relevantMessages) : Collections.emptyList();
        relevantImages = relevantImages != null ? Collections.unmodifiableList(relevantImages) : Collections.emptyList();
    }

    /**
     * Net liquid buffer on request_date:
     * availableBalance - reservedPendingDebits - minimumBalanceToKeep.
     */
    public BigDecimal netImmediateBuffer() {
        BigDecimal available = startingBalance != null ? startingBalance : BigDecimal.ZERO;
        BigDecimal reserved = reservedPendingDebits != null ? reservedPendingDebits : BigDecimal.ZERO;
        BigDecimal min = minimumBalanceToKeep != null ? minimumBalanceToKeep : BigDecimal.ZERO;
        return available.subtract(reserved).subtract(min);
    }

    public boolean hasPendingDebits() {
        return !pendingDebits.isEmpty();
    }

    public FinancialEvent getEventById(String eventId) {
        if (eventId == null) return null;
        for (FinancialEvent ev : flexibleExpenses) {
            if (eventId.equals(ev.eventId())) return ev;
        }
        for (FinancialEvent ev : essentialExpenses) {
            if (eventId.equals(ev.eventId())) return ev;
        }
        for (FinancialEvent ev : pendingDebits) {
            if (eventId.equals(ev.eventId())) return ev;
        }
        for (FinancialEvent ev : confirmedIncomeEvents) {
            if (eventId.equals(ev.eventId())) return ev;
        }
        for (FinancialEvent ev : historicalEvents) {
            if (eventId.equals(ev.eventId())) return ev;
        }
        return null;
    }

    public List<FinancialEvent> allEvents() {
        List<FinancialEvent> all = new java.util.ArrayList<>();
        all.addAll(flexibleExpenses);
        all.addAll(essentialExpenses);
        all.addAll(pendingDebits);
        all.addAll(confirmedIncomeEvents);
        all.addAll(historicalEvents);
        return Collections.unmodifiableList(all);
    }

    public FinancialEvent findNextConfirmedSalary() {
        for (FinancialEvent ev : confirmedIncomeEvents) {
            if ("salary".equalsIgnoreCase(ev.category()) || (ev.description() != null && ev.description().toLowerCase().contains("salary"))) {
                return ev;
            }
        }
        for (FinancialEvent ev : historicalEvents) {
            if ("salary".equalsIgnoreCase(ev.category()) || (ev.description() != null && ev.description().toLowerCase().contains("salary"))) {
                return ev;
            }
        }
        return null;
    }

    public String homeCurrency() {
        return profile != null && profile.homeCurrency() != null ? profile.homeCurrency() : "INR";
    }
}

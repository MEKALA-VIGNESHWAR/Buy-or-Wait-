package com.orchestrate.buywait.engine;

import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Daily balance breakdown for a specific date in the 90-day forecast horizon.
 * <p>
 * Daily calculation:
 * closingBalance = openingBalance + confirmedIncome - mandatoryExpenses - otherOutflows - proposedPayments
 */
public record ForecastDay(
        LocalDate date,
        BigDecimal openingBalance,
        BigDecimal confirmedIncome,
        BigDecimal mandatoryExpenses,
        BigDecimal otherOutflows,
        BigDecimal proposedPayments,
        BigDecimal closingBalance,
        List<FinancialEvent> appliedEvents,
        List<Payment> appliedPayments,
        boolean minimumBalanceViolated
) {
    public ForecastDay {
        openingBalance = openingBalance != null ? openingBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        confirmedIncome = confirmedIncome != null ? confirmedIncome.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        mandatoryExpenses = mandatoryExpenses != null ? mandatoryExpenses.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        otherOutflows = otherOutflows != null ? otherOutflows.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        proposedPayments = proposedPayments != null ? proposedPayments.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        closingBalance = closingBalance != null ? closingBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        appliedEvents = appliedEvents != null ? Collections.unmodifiableList(appliedEvents) : Collections.emptyList();
        appliedPayments = appliedPayments != null ? Collections.unmodifiableList(appliedPayments) : Collections.emptyList();
    }

    public BigDecimal netDailyCashFlow() {
        return confirmedIncome.subtract(mandatoryExpenses).subtract(otherOutflows).subtract(proposedPayments);
    }
}

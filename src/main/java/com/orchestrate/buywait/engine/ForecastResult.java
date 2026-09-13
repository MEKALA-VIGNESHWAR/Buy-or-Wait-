package com.orchestrate.buywait.engine;

import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the complete outcome of a 90-day cash flow simulation.
 */
public record ForecastResult(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal startingBalance,
        BigDecimal minimumBalanceToKeep,
        BigDecimal minimumBalanceObserved,
        LocalDate dateOfMinimumBalance,
        boolean minimumBalanceViolated,
        BigDecimal finalBalance,
        Map<LocalDate, BigDecimal> balanceByDate,
        List<ForecastDay> dailyForecasts,
        List<FinancialEvent> appliedCashFlowEvents,
        List<Payment> appliedPayments,
        boolean allPaymentsAffordable
) {
    public ForecastResult {
        startingBalance = startingBalance != null ? startingBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        minimumBalanceToKeep = minimumBalanceToKeep != null ? minimumBalanceToKeep.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        minimumBalanceObserved = minimumBalanceObserved != null ? minimumBalanceObserved.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        finalBalance = finalBalance != null ? finalBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        balanceByDate = balanceByDate != null ? Collections.unmodifiableMap(balanceByDate) : Collections.emptyMap();
        dailyForecasts = dailyForecasts != null ? Collections.unmodifiableList(dailyForecasts) : Collections.emptyList();
        appliedCashFlowEvents = appliedCashFlowEvents != null ? Collections.unmodifiableList(appliedCashFlowEvents) : Collections.emptyList();
        appliedPayments = appliedPayments != null ? Collections.unmodifiableList(appliedPayments) : Collections.emptyList();
    }

    public BigDecimal getBalanceOn(LocalDate date) {
        return balanceByDate.get(date);
    }
}

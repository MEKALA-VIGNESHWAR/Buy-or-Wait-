package com.orchestrate.buywait.engine;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic 90-day cash flow simulation engine.
 * Evaluates affordability and balance trajectory from request_date to request_date + 90 days.
 */
public interface ForecastSimulationEngine {

    int FORECAST_HORIZON_DAYS = 90;

    /**
     * Runs deterministic 90-day cash-flow forecast for the given proposed plan without spending changes.
     */
    default ForecastResult simulate(FinancialState financialState, List<Payment> proposedPlan) {
        return simulate(financialState, proposedPlan, Collections.emptySet(), Collections.emptyMap());
    }

    /**
     * Runs deterministic 90-day cash-flow forecast incorporating optional spending changes.
     */
    ForecastResult simulate(
            FinancialState financialState,
            List<Payment> proposedPlan,
            Set<String> stoppedEventIds,
            Map<String, BigDecimal> reducedAmounts
    );

    /**
     * Checks if a proposed plan is safe without spending changes.
     */
    default boolean isSafe(List<Payment> proposedPlan, FinancialState financialState) {
        return isSafe(proposedPlan, financialState, Collections.emptySet(), Collections.emptyMap());
    }

    /**
     * Checks if a proposed plan is safe with specified spending changes:
     * 1. Every planned payment is affordable.
     * 2. Balance never falls below minimum_balance_to_keep.
     * 3. Request is fully completed (sum of payments == requested_amount).
     * 4. Completion date satisfies desired_completion_date.
     */
    boolean isSafe(
            List<Payment> proposedPlan,
            FinancialState financialState,
            Set<String> stoppedEventIds,
            Map<String, BigDecimal> reducedAmounts
    );

    /**
     * Calculates the maximum safe amount the user can pay on request_date
     * before optional spending changes, capped between 0 and requested_amount.
     */
    BigDecimal calculateAmountSafeToPay(FinancialState financialState);

    /**
     * Finds the earliest date in the 90-day horizon on which the full requested amount
     * can be paid safely without spending changes.
     */
    Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState, BigDecimal requestedAmount);
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.engine.ForecastResult;
import com.orchestrate.buywait.engine.ForecastSimulationEngine;
import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic service to calculate the maximum amount safe to pay on request_date
 * before optional spending changes, satisfying the 90-day safety check.
 * <p>
 * =========================================================================================
 * MATHEMATICAL PROOF OF MONOTONICITY & SEARCH VALIDITY
 * =========================================================================================
 * Let X >= 0 be a candidate payment made on request_date (t = 0).
 * Let f(X) be the minimum closing balance observed during the 90-day simulation:
 *      f(X) = min_{t in [0, 90]} closingBalance(t; X)
 * <p>
 * On request_date (t = 0):
 *      closingBalance(0; X) = openingBalance(0) + netCashFlow(0) - X
 * On all subsequent days t in [1, 90], since scheduled income, essential expenses, and other
 * cash flows do not depend on the request payment X, the closing balance satisfies:
 *      closingBalance(t; X) = closingBalance(t; 0) - X
 * <p>
 * Therefore:
 *      f(X) = min_{t in [0, 90]} (closingBalance(t; 0) - X)
 *           = (min_{t in [0, 90]} closingBalance(t; 0)) - X
 *           = B_min - X
 * where B_min = f(0) is the lowest balance reached during the baseline simulation.
 * <p>
 * 1. STRICT MONOTONICITY:
 *    df(X)/dX = -1 < 0.
 *    For any X_1 < X_2:
 *        f(X_1) > f(X_2)
 *    Hence the predicate:
 *        IsSafe(X) <=> f(X) >= minimum_balance_to_keep
 *    is strictly monotonically decreasing (true for X <= X*, false for X > X*).
 *    This guarantees that monotonic binary search over [0, requested_amount] converges
 *    deterministically to the global supremum.
 * <p>
 * 2. EXACT CLOSED-FORM SUPREMUM:
 *    f(X) >= M  <=>  B_min - X >= M  <=>  X <= B_min - M
 *    Capping between 0 and requested_amount (R):
 *        X* = min(R, max(0, B_min - M))
 * =========================================================================================
 */
@Service
public class AmountSafeToPayServiceImpl implements AmountSafeToPayService {

    private static final Logger log = LoggerFactory.getLogger(AmountSafeToPayServiceImpl.class);

    private static final BigDecimal STEP_CENT = new BigDecimal("0.01");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final ForecastSimulationEngine forecastEngine;

    public AmountSafeToPayServiceImpl(ForecastSimulationEngine forecastEngine) {
        this.forecastEngine = forecastEngine;
    }

    @Override
    public BigDecimal calculateAmountSafeToPay(FinancialState financialState) {
        if (financialState == null || financialState.request() == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        BigDecimal requestedAmount = financialState.request().requestedAmount();
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        BigDecimal minKeep = financialState.minimumBalanceToKeep() != null
                ? financialState.minimumBalanceToKeep()
                : BigDecimal.ZERO;

        // 1. Run baseline 90-day simulation with 0 payments
        ForecastResult baseline = forecastEngine.simulate(financialState, Collections.emptyList());

        // 2. If baseline itself already violates minimum balance, safe amount is strictly 0
        if (baseline.minimumBalanceViolated() || baseline.minimumBalanceObserved().compareTo(minKeep) <= 0) {
            log.debug("User {} baseline minimum balance {} <= required {}; amount safe to pay is 0",
                    financialState.profile().userId(), baseline.minimumBalanceObserved(), minKeep);
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // 3. Exact supremum buffer above minimum balance over the entire 90-day window
        BigDecimal maxCapacity = baseline.minimumBalanceObserved().subtract(minKeep);
        if (maxCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // 4. Cap at requested_amount
        BigDecimal safe = maxCapacity.min(requestedAmount);
        return safe.setScale(SCALE, ROUNDING);
    }

    @Override
    public BigDecimal calculateAmountSafeToPayWithBinarySearch(FinancialState financialState) {
        if (financialState == null || financialState.request() == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        BigDecimal requestedAmount = financialState.request().requestedAmount();
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        LocalDate requestDate = financialState.request().requestDate();

        // Check if even 0 payment is unsafe
        if (!isPaymentSafe(BigDecimal.ZERO, requestDate, financialState)) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // Check if full requested amount is safe
        if (isPaymentSafe(requestedAmount, requestDate, financialState)) {
            return requestedAmount.setScale(SCALE, ROUNDING);
        }

        // Monotonic Binary Search in cents: [0.00, requestedAmount]
        long lowCents = 0;
        long highCents = requestedAmount.multiply(BigDecimal.valueOf(100)).longValue();
        long bestCents = 0;

        while (lowCents <= highCents) {
            long midCents = lowCents + (highCents - lowCents) / 2;
            BigDecimal candidate = BigDecimal.valueOf(midCents, SCALE);

            if (isPaymentSafe(candidate, requestDate, financialState)) {
                bestCents = midCents;
                lowCents = midCents + 1; // Try larger payment
            } else {
                highCents = midCents - 1; // Exceeded safety threshold
            }
        }

        return BigDecimal.valueOf(bestCents, SCALE);
    }

    private boolean isPaymentSafe(BigDecimal paymentAmount, LocalDate requestDate, FinancialState financialState) {
        List<Payment> paymentList = paymentAmount.compareTo(BigDecimal.ZERO) > 0
                ? List.of(new Payment(requestDate, paymentAmount))
                : Collections.emptyList();

        ForecastResult result = forecastEngine.simulate(financialState, paymentList);
        return !result.minimumBalanceViolated() && result.allPaymentsAffordable();
    }
}

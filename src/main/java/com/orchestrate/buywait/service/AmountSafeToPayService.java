package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;

import java.math.BigDecimal;

/**
 * Service to calculate amount_safe_to_pay on request_date.
 * <p>
 * Definition:
 * The maximum amount the user can pay on request_date BEFORE optional spending changes
 * while still satisfying the 90-day safety check.
 * <p>
 * Constraints:
 * 0 <= amount_safe_to_pay <= requested_amount
 */
public interface AmountSafeToPayService {

    /**
     * Calculates the maximum safe amount the user can pay on request_date
     * before optional spending changes, capped between 0 and requested_amount.
     */
    BigDecimal calculateAmountSafeToPay(FinancialState financialState);

    /**
     * Performs a monotonic binary search over [0, requested_amount] testing candidate payments
     * on request_date against the 90-day forecast engine to find the exact supremum.
     */
    BigDecimal calculateAmountSafeToPayWithBinarySearch(FinancialState financialState);
}

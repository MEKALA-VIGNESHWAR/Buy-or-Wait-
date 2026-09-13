package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Service to calculate earliest_date_for_full_payment.
 * <p>
 * Definition:
 * The earliest date on which paying the FULL requested amount as one single payment
 * passes the 90-day safety check, WITHOUT optional spending changes.
 * <p>
 * Important:
 * This calculation is independent of the user's payment-method preferences.
 */
public interface EarliestFullPaymentService {

    /**
     * Finds the earliest date for full payment of request.requestedAmount().
     * Returns empty if no date in the 90-day horizon is safe.
     */
    Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState);

    /**
     * Finds the earliest date for full payment of a specific custom amount.
     */
    Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState, BigDecimal amount);
}

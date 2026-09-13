package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Plan;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.VerificationResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Independent deterministic verifier for financial plans and predictions.
 *
 * Enforces all 16 challenge verification rules without trusting the plan generator:
 * 1. amount_safe_to_pay is between 0 and requested_amount.
 * 2. payment dates are chronological.
 * 3. every payment amount is positive where applicable.
 * 4. sum of payments equals requested_amount when a complete plan is claimed.
 * 5. payment plan finishes by desired_completion_date.
 * 6. every payment is financially feasible.
 * 7. balance never falls below minimum_balance_to_keep.
 * 8. installment schedules exactly match the supplied payment option.
 * 9. partial-payment plans contain exactly two payments.
 * 10. partial-payment plans satisfy all partial-payment eligibility rules.
 * 11. immediate payment methods are accepted by the user.
 * 12. wait is only used when full payment becomes safe later and is accepted.
 * 13. spending changes target only flexible recurring expenses.
 * 14. maximum three spending changes.
 * 15. stop/reduce conflict is impossible.
 * 16. no unsupported financial information is introduced.
 */
public interface PlanVerifier {

    /**
     * Verifies a proposed Plan against the user's financial state and calculated boundaries.
     *
     * @param plan the proposed payment plan
     * @param amountSafeToPay amount safe to pay on request date
     * @param earliestDateForFullPayment earliest safe date for full payment
     * @param financialState reconstructed financial state
     * @return VerificationResult indicating validity and listing any violations
     */
    VerificationResult verifyPlan(
            Plan plan,
            BigDecimal amountSafeToPay,
            LocalDate earliestDateForFullPayment,
            FinancialState financialState
    );

    /**
     * Verifies an end-to-end Prediction row against the user's reconstructed financial state.
     *
     * @param prediction the final output prediction
     * @param financialState reconstructed financial state
     * @return VerificationResult indicating validity and listing any violations
     */
    VerificationResult verifyPrediction(
            Prediction prediction,
            FinancialState financialState
    );
}

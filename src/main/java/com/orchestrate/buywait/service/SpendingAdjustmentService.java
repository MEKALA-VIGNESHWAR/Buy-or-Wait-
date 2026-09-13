package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Payment;
import com.orchestrate.buywait.model.SpendingChange;

import java.util.List;
import java.util.Optional;

/**
 * Service to determine whether a requested payment plan can become safely affordable
 * by modifying flexible recurring expenses.
 *
 * Rules:
 * 1. Only recurring expenses marked flexible may be changed.
 * 2. A maximum of three spending changes may be recommended.
 * 3. Supported changes:
 *    - stop:<event_id>
 *    - reduce_to:<event_id>:<new_amount>
 * 4. The same event cannot be both stopped and reduced.
 * 5. Do not modify essential/non-flexible expenses.
 * 6. Do not invent new expenses.
 * 7. Do not reduce an expense below zero.
 * 8. Preserve chronological recurrence behavior.
 * 9. Re-run the 90-day forecast after each candidate change set.
 *
 * Ranking criteria:
 * 1. Fewer changes
 * 2. Lower disruption
 * 3. Greater financial safety
 * 4. Ability to complete request
 */
public interface SpendingAdjustmentService {

    /**
     * Determines whether a proposed payment plan can become safely affordable by modifying
     * flexible recurring expenses, and returns the optimal ranked set of spending changes (up to 3).
     *
     * @param proposedPlan payments of the candidate plan (e.g., immediate full payment, installment schedule)
     * @param financialState reconstructed financial state of the user
     * @return Optional containing the best ranked list of spending changes (up to 3), or empty if unattainable
     */
    Optional<List<SpendingChange>> findSpendingChangesToMakeSafe(
            List<Payment> proposedPlan,
            FinancialState financialState
    );

    /**
     * Finds all valid candidate spending adjustment combinations that make the proposed plan
     * safely affordable, ranked in order of preference.
     *
     * @param proposedPlan payments of the candidate plan
     * @param financialState reconstructed financial state of the user
     * @return ranked list of viable spending change combinations
     */
    List<List<SpendingChange>> findRankedCandidateSpendingChanges(
            List<Payment> proposedPlan,
            FinancialState financialState
    );

    /**
     * Retrieves all permitted individual candidate spending changes (stop and/or reduce_to)
     * for the user's flexible recurring expenses based on user profile preferences.
     *
     * @param financialState reconstructed financial state of the user
     * @return list of eligible individual spending changes
     */
    List<SpendingChange> getEligibleCandidateChanges(FinancialState financialState);
}

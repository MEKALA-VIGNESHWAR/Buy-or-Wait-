package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Plan;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service to deterministically rank safe and eligible payment plans according
 * to the official HackerRank challenge contract.
 *
 * Ranking criteria in exact order of precedence:
 * 1. Complete the full request by desired_completion_date.
 * 2. Require no spending changes.
 * 3. Minimize the total amount paid.
 * 4. Start payment earlier.
 * 5. Use fewer payments.
 * 6. Lowest payment_option_id as final tie-breaker.
 */
public interface PlanRanker {

    /**
     * Filters for safe, eligible plans and ranks them according to the 6 official criteria.
     *
     * @param candidatePlans list of candidate plans
     * @param desiredCompletionDate desired completion date of the request
     * @return sorted list of plans, best plan first
     */
    List<Plan> rankPlans(List<Plan> candidatePlans, LocalDate desiredCompletionDate);

    /**
     * Selects the single top-ranked plan from the candidates.
     *
     * @param candidatePlans list of candidate plans
     * @param desiredCompletionDate desired completion date of the request
     * @return Optional containing the best plan, or empty if no safe plan exists
     */
    Optional<Plan> selectBestPlan(List<Plan> candidatePlans, LocalDate desiredCompletionDate);

    /**
     * Selects the single top-ranked plan from the candidates for the given financial state.
     *
     * @param candidatePlans list of candidate plans
     * @param financialState financial state containing the request and profile
     * @return Optional containing the best plan, or empty if no safe plan exists
     */
    Optional<Plan> selectBestPlan(List<Plan> candidatePlans, FinancialState financialState);

    /**
     * Creates a deterministic Comparator for ranking plans against a desired completion date.
     *
     * @param desiredCompletionDate desired completion date of the request
     * @return deterministic Plan Comparator
     */
    Comparator<Plan> createPlanComparator(LocalDate desiredCompletionDate);
}

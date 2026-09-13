package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.model.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Deterministic implementation of {@link PlanRanker}.
 *
 * Implements the official 6-step ranking hierarchy from the problem specification:
 * 1. Complete the full request by desired_completion_date.
 * 2. Require no spending changes.
 * 3. Minimize total amount paid.
 * 4. Start payment earlier.
 * 5. Use fewer payments.
 * 6. Lowest payment_option_id as final tie-breaker.
 *
 * No LLM or heuristic choices are used. Ranking is strictly deterministic.
 */
@Service
public class PlanRankerImpl implements PlanRanker {

    private static final Logger log = LoggerFactory.getLogger(PlanRankerImpl.class);

    @Override
    public List<Plan> rankPlans(List<Plan> candidatePlans, LocalDate desiredCompletionDate) {
        if (candidatePlans == null || candidatePlans.isEmpty()) {
            return Collections.emptyList();
        }

        // Only safe and eligible plans may reach the ranking stage
        List<Plan> validCandidates = candidatePlans.stream()
                .filter(this::isCandidateValid)
                .collect(Collectors.toList());

        if (validCandidates.isEmpty()) {
            log.debug("No valid safe/eligible candidate plans available for ranking.");
            return Collections.emptyList();
        }

        Comparator<Plan> comparator = createPlanComparator(desiredCompletionDate);
        validCandidates.sort(comparator);

        log.debug("Ranked {} candidate plans for desired completion date {}", validCandidates.size(), desiredCompletionDate);
        return Collections.unmodifiableList(validCandidates);
    }

    @Override
    public Optional<Plan> selectBestPlan(List<Plan> candidatePlans, LocalDate desiredCompletionDate) {
        List<Plan> ranked = rankPlans(candidatePlans, desiredCompletionDate);
        return ranked.isEmpty() ? Optional.empty() : Optional.of(ranked.get(0));
    }

    @Override
    public Optional<Plan> selectBestPlan(List<Plan> candidatePlans, FinancialState financialState) {
        LocalDate deadline = (financialState != null && financialState.request() != null)
                ? financialState.request().desiredCompletionDate()
                : null;
        return selectBestPlan(candidatePlans, deadline);
    }

    @Override
    public Comparator<Plan> createPlanComparator(LocalDate desiredCompletionDate) {
        return (p1, p2) -> {
            // =========================================================================
            // Criterion 1: Complete the full request by desired_completion_date.
            // A plan completing on or before desired_completion_date is strictly preferred
            // over a plan completing after the desired deadline.
            // =========================================================================
            boolean p1OnTime = isOnTime(p1, desiredCompletionDate);
            boolean p2OnTime = isOnTime(p2, desiredCompletionDate);
            if (p1OnTime != p2OnTime) {
                return p1OnTime ? -1 : 1;
            }

            // =========================================================================
            // Criterion 2: Require no spending changes.
            // Plans requiring zero spending changes are strictly preferred over plans
            // requiring cuts or cancellations of flexible recurring expenses.
            // If both require changes, the plan with fewer changes is preferred.
            // =========================================================================
            boolean p1NoChanges = !p1.requiresSpendingChanges();
            boolean p2NoChanges = !p2.requiresSpendingChanges();
            if (p1NoChanges != p2NoChanges) {
                return p1NoChanges ? -1 : 1;
            }

            // =========================================================================
            // Criterion 3: Minimize total amount paid.
            // Plans with lower total cost (e.g., zero interest/fees vs financing fee)
            // are strictly preferred.
            // =========================================================================
            BigDecimal total1 = p1.totalAmount() != null ? p1.totalAmount() : BigDecimal.ZERO;
            BigDecimal total2 = p2.totalAmount() != null ? p2.totalAmount() : BigDecimal.ZERO;
            int totalCmp = total1.compareTo(total2);
            if (totalCmp != 0) {
                return totalCmp;
            }

            // =========================================================================
            // Criterion 4: Start payment earlier.
            // A plan where the user begins committing funds earlier is preferred
            // (e.g., immediate partial payment vs waiting weeks for a deferred full payment).
            // =========================================================================
            LocalDate start1 = p1.getStartDate();
            LocalDate start2 = p2.getStartDate();
            if (start1 != null && start2 != null) {
                int startCmp = start1.compareTo(start2);
                if (startCmp != 0) {
                    return startCmp;
                }
            } else if (start1 != null || start2 != null) {
                return start1 != null ? -1 : 1;
            }

            // =========================================================================
            // Criterion 5: Use fewer payments.
            // A plan with fewer installments (e.g., 1 payment vs 3 payments) is
            // preferred because it is simpler and settles the commitment faster.
            // =========================================================================
            int countCmp = Integer.compare(p1.paymentCount(), p2.paymentCount());
            if (countCmp != 0) {
                return countCmp;
            }

            // =========================================================================
            // Criterion 6: Lowest payment_option_id as final tie-breaker.
            // When multiple seller options offer identical terms, break ties
            // lexicographically using the payment_option_id.
            // =========================================================================
            String opt1 = p1.paymentOptionId();
            String opt2 = p2.paymentOptionId();
            if (opt1 != null && opt2 != null) {
                int optCmp = opt1.compareTo(opt2);
                if (optCmp != 0) {
                    return optCmp;
                }
            } else if (opt1 != null || opt2 != null) {
                return opt1 != null ? -1 : 1;
            }

            // =========================================================================
            // Deterministic Tie-Breaker:
            // Ensure absolute determinism across identical plans.
            // =========================================================================
            if (p1.completionDate() != null && p2.completionDate() != null) {
                int completionCmp = p1.completionDate().compareTo(p2.completionDate());
                if (completionCmp != 0) {
                    return completionCmp;
                }
            }
            int methodCmp = p1.paymentMethod().name().compareTo(p2.paymentMethod().name());
            if (methodCmp != 0) {
                return methodCmp;
            }
            return p1.toPlanString().compareTo(p2.toPlanString());
        };
    }

    /**
     * Determines whether the plan completes on or before the desired deadline.
     */
    private boolean isOnTime(Plan plan, LocalDate desiredCompletionDate) {
        if (desiredCompletionDate == null) {
            return true;
        }
        LocalDate completionDate = plan.completionDate();
        if (completionDate == null) {
            return false;
        }
        return !completionDate.isAfter(desiredCompletionDate);
    }

    /**
     * Validates that a candidate plan is eligible, safe, and actionable.
     */
    private boolean isCandidateValid(Plan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.paymentMethod() == PaymentMethod.not_recommended) {
            return false;
        }
        if (!plan.isSafePlan()) {
            return false;
        }
        return !plan.payments().isEmpty();
    }
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.engine.ForecastResult;
import com.orchestrate.buywait.engine.ForecastSimulationEngine;
import com.orchestrate.buywait.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Independent deterministic verifier for proposed plans and predictions.
 * Does not trust the plan generator; independently validates all 16 challenge rules.
 */
@Service
public class PlanVerifierImpl implements PlanVerifier {

    private static final Logger log = LoggerFactory.getLogger(PlanVerifierImpl.class);

    private final ForecastSimulationEngine forecastEngine;

    public PlanVerifierImpl(ForecastSimulationEngine forecastEngine) {
        this.forecastEngine = forecastEngine;
    }

    @Override
    public VerificationResult verifyPlan(
            Plan plan,
            BigDecimal amountSafeToPay,
            LocalDate earliestDateForFullPayment,
            FinancialState financialState
    ) {
        List<String> violations = new ArrayList<>();

        if (financialState == null || financialState.request() == null || financialState.profile() == null) {
            violations.add("Financial state, request, or profile is null");
            return VerificationResult.invalid(violations);
        }

        Request request = financialState.request();
        FinancialProfile profile = financialState.profile();
        BigDecimal requestedAmount = request.requestedAmount();
        LocalDate requestDate = request.requestDate();
        LocalDate desiredCompletionDate = request.desiredCompletionDate();

        // -------------------------------------------------------------------------
        // Rule 1: amount_safe_to_pay is between 0 and requested_amount.
        // -------------------------------------------------------------------------
        if (amountSafeToPay == null
                || amountSafeToPay.compareTo(BigDecimal.ZERO) < 0
                || amountSafeToPay.compareTo(requestedAmount) > 0) {
            violations.add(String.format("Rule 1 Violation: amount_safe_to_pay (%s) must be between 0 and requested_amount (%s)",
                    amountSafeToPay, requestedAmount));
        }

        if (plan == null) {
            violations.add("Plan is null");
            return VerificationResult.invalid(violations);
        }

        List<Payment> payments = plan.payments();
        PaymentMethod method = plan.paymentMethod();

        // If plan is not_recommended, verify that it has no payments
        if (method == PaymentMethod.not_recommended) {
            if (!payments.isEmpty()) {
                violations.add("Rule 4 Violation: not_recommended plan must not contain scheduled payments");
            }
            return violations.isEmpty() ? VerificationResult.ok() : VerificationResult.invalid(violations);
        }

        // -------------------------------------------------------------------------
        // Rule 2: payment dates are chronological.
        // -------------------------------------------------------------------------
        for (int i = 0; i < payments.size() - 1; i++) {
            LocalDate d1 = payments.get(i).paymentDate();
            LocalDate d2 = payments.get(i + 1).paymentDate();
            if (!d1.isBefore(d2)) {
                violations.add(String.format("Rule 2 Violation: payment dates must be strictly chronological, found %s followed by %s",
                        d1, d2));
            }
        }

        // -------------------------------------------------------------------------
        // Rule 3: every payment amount is positive where applicable.
        // -------------------------------------------------------------------------
        for (Payment p : payments) {
            if (p.amount() == null || p.amount().compareTo(BigDecimal.ZERO) <= 0) {
                violations.add(String.format("Rule 3 Violation: every payment amount must be positive, found %s on %s",
                        p.amount(), p.paymentDate()));
            }
        }

        // -------------------------------------------------------------------------
        // Rule 4: sum of payments equals requested_amount when complete plan is claimed.
        // (For installments, sum must match the supplied option's total payable amount).
        // -------------------------------------------------------------------------
        BigDecimal totalPayments = calculateTotal(payments);
        if (method == PaymentMethod.full_payment || method == PaymentMethod.partial_payment || method == PaymentMethod.wait) {
            if (totalPayments.compareTo(requestedAmount) != 0) {
                violations.add(String.format("Rule 4 Violation: sum of payments (%s) must equal requested_amount (%s) for %s",
                        totalPayments, requestedAmount, method));
            }
        } else if (method == PaymentMethod.installments) {
            PaymentOption opt = findOptionById(financialState, plan.paymentOptionId());
            if (opt != null) {
                BigDecimal expectedTotal = opt.totalPayableAmount() != null ? opt.totalPayableAmount() : requestedAmount;
                if (totalPayments.compareTo(expectedTotal) != 0) {
                    violations.add(String.format("Rule 4 Violation: installment payments sum (%s) does not match option total (%s)",
                            totalPayments, expectedTotal));
                }
            }
        }

        // -------------------------------------------------------------------------
        // Rule 5: payment plan finishes by desired_completion_date.
        // -------------------------------------------------------------------------
        if (desiredCompletionDate != null && !payments.isEmpty()) {
            LocalDate completionDate = payments.get(payments.size() - 1).paymentDate();
            if (completionDate.isAfter(desiredCompletionDate)) {
                violations.add(String.format("Rule 5 Violation: plan finishes on %s, which is after desired_completion_date %s",
                        completionDate, desiredCompletionDate));
            }
        }

        // -------------------------------------------------------------------------
        // Rule 8: installment schedules exactly match the supplied payment option.
        // -------------------------------------------------------------------------
        if (method == PaymentMethod.installments) {
            String optId = plan.paymentOptionId();
            if (optId == null || optId.isBlank()) {
                violations.add("Rule 8 Violation: installment plan must reference a valid payment_option_id");
            } else {
                PaymentOption opt = findOptionById(financialState, optId);
                if (opt == null) {
                    violations.add("Rule 8 Violation: payment option not found in available options: " + optId);
                } else {
                    if (!opt.isInstallments()) {
                        violations.add("Rule 8 Violation: referenced option is not an installment option: " + optId);
                    }
                    if (opt.numberOfPayments() != null && opt.numberOfPayments() != payments.size()) {
                        violations.add(String.format("Rule 8 Violation: installment payment count (%d) does not match option (%d)",
                                payments.size(), opt.numberOfPayments()));
                    }
                    if (opt.firstPaymentDate() != null && !payments.isEmpty() && !payments.get(0).paymentDate().equals(opt.firstPaymentDate())) {
                        violations.add(String.format("Rule 8 Violation: first payment date (%s) does not match option (%s)",
                                payments.get(0).paymentDate(), opt.firstPaymentDate()));
                    }
                    // Validate frequency spacing
                    int expectedFreq = opt.paymentFrequencyDays() != null && opt.paymentFrequencyDays() > 0 ? opt.paymentFrequencyDays() : 30;
                    for (int i = 0; i < payments.size() - 1; i++) {
                        LocalDate expectedNext = payments.get(i).paymentDate().plusDays(expectedFreq);
                        if (!payments.get(i + 1).paymentDate().equals(expectedNext)) {
                            violations.add(String.format("Rule 8 Violation: payment %d date %s does not match frequency spacing of %d days from %s (expected %s)",
                                    i + 2, payments.get(i + 1).paymentDate(), expectedFreq, payments.get(i).paymentDate(), expectedNext));
                        }
                    }
                    // Validate user max installment months
                    Integer maxMonths = profile.maxInstallmentMonths();
                    if (maxMonths != null && opt.numberOfPayments() != null && opt.numberOfPayments() > maxMonths) {
                        violations.add(String.format("Rule 8 Violation: option payment count (%d) exceeds user max_installment_months (%d)",
                                opt.numberOfPayments(), maxMonths));
                    }
                }
            }
        }

        // -------------------------------------------------------------------------
        // Rule 9 & 10: partial-payment plans contain exactly two payments and satisfy eligibility.
        // -------------------------------------------------------------------------
        if (method == PaymentMethod.partial_payment) {
            if (payments.size() != 2) {
                violations.add("Rule 9 Violation: partial-payment plan must contain exactly two payments, found: " + payments.size());
            }
            if (!request.allowsPartialPayment()) {
                violations.add("Rule 10 Violation: request does not permit partial payment (allows_partial_payment=false)");
            }
            if (!profile.considersMethod(PaymentMethod.partial_payment)) {
                violations.add("Rule 10 Violation: user profile does not consider partial_payment");
            }
            if (amountSafeToPay == null || amountSafeToPay.compareTo(BigDecimal.ZERO) <= 0 || amountSafeToPay.compareTo(requestedAmount) >= 0) {
                violations.add(String.format("Rule 10 Violation: amount_safe_to_pay (%s) must be strictly between 0 and requested_amount (%s) for partial payment",
                        amountSafeToPay, requestedAmount));
            }
            if (earliestDateForFullPayment == null) {
                violations.add("Rule 10 Violation: earliest_date_for_full_payment must be present for partial payment");
            } else if (desiredCompletionDate != null && earliestDateForFullPayment.isAfter(desiredCompletionDate)) {
                violations.add(String.format("Rule 10 Violation: earliest_date_for_full_payment (%s) is after desired_completion_date (%s)",
                        earliestDateForFullPayment, desiredCompletionDate));
            }
            if (payments.size() == 2) {
                Payment p1 = payments.get(0);
                Payment p2 = payments.get(1);
                if (!p1.paymentDate().equals(requestDate)) {
                    violations.add(String.format("Rule 10 Violation: first partial payment must be on request_date (%s), found %s",
                            requestDate, p1.paymentDate()));
                }
                if (amountSafeToPay != null && p1.amount().compareTo(amountSafeToPay) != 0) {
                    violations.add(String.format("Rule 10 Violation: first partial payment amount (%s) must equal amount_safe_to_pay (%s)",
                            p1.amount(), amountSafeToPay));
                }
                if (earliestDateForFullPayment != null && !p2.paymentDate().equals(earliestDateForFullPayment)) {
                    violations.add(String.format("Rule 10 Violation: second partial payment date (%s) must equal earliest_date_for_full_payment (%s)",
                            p2.paymentDate(), earliestDateForFullPayment));
                }
                BigDecimal expectedRemaining = requestedAmount.subtract(amountSafeToPay != null ? amountSafeToPay : BigDecimal.ZERO);
                if (p2.amount().compareTo(expectedRemaining) != 0) {
                    violations.add(String.format("Rule 10 Violation: second partial payment amount (%s) must equal requested_amount - amount_safe_to_pay (%s)",
                            p2.amount(), expectedRemaining));
                }
            }
        }

        // -------------------------------------------------------------------------
        // Rule 11: immediate payment methods are accepted by the user.
        // -------------------------------------------------------------------------
        if (method == PaymentMethod.full_payment || method == PaymentMethod.partial_payment || method == PaymentMethod.installments) {
            if (!profile.considersMethod(method)) {
                violations.add(String.format("Rule 11 Violation: immediate payment method %s is not accepted by user in payment_methods_user_will_consider", method));
            }
        }

        // -------------------------------------------------------------------------
        // Rule 12: wait is only used when full payment becomes safe later and is accepted.
        // -------------------------------------------------------------------------
        if (method == PaymentMethod.wait) {
            if (!profile.considersMethod(PaymentMethod.full_payment)) {
                violations.add("Rule 12 Violation: wait requires user to accept full_payment");
            }
            if (earliestDateForFullPayment == null || earliestDateForFullPayment.equals(requestDate)) {
                violations.add(String.format("Rule 12 Violation: wait is only valid when full payment becomes safe strictly after request_date (%s), found %s",
                        requestDate, earliestDateForFullPayment));
            }
            if (payments.size() != 1) {
                violations.add("Rule 12 Violation: wait plan must contain exactly one payment, found: " + payments.size());
            } else if (earliestDateForFullPayment != null && !payments.get(0).paymentDate().equals(earliestDateForFullPayment)) {
                violations.add(String.format("Rule 12 Violation: wait payment date (%s) must equal earliest_date_for_full_payment (%s)",
                        payments.get(0).paymentDate(), earliestDateForFullPayment));
            }
            if (desiredCompletionDate != null && earliestDateForFullPayment != null && earliestDateForFullPayment.isAfter(desiredCompletionDate)) {
                violations.add(String.format("Rule 12 Violation: wait date (%s) exceeds desired_completion_date (%s)",
                        earliestDateForFullPayment, desiredCompletionDate));
            }
        }

        // -------------------------------------------------------------------------
        // Rule 13: spending changes target only flexible recurring expenses.
        // -------------------------------------------------------------------------
        List<SpendingChange> spendingChanges = plan.spendingChanges();
        for (SpendingChange sc : spendingChanges) {
            FinancialEvent ev = financialState.getEventById(sc.eventId());
            if (ev == null) {
                violations.add("Rule 13 Violation: spending change targets non-existent event: " + sc.eventId());
                continue;
            }
            boolean isFlexible = financialState.flexibleExpenses().stream().anyMatch(e -> e.eventId().equals(sc.eventId()));
            if (!isFlexible) {
                violations.add("Rule 13 Violation: spending change targets non-flexible or non-recurring expense: " + sc.eventId());
            }
            if (profile.isCategoryProtected(ev.category())) {
                violations.add(String.format("Rule 13 Violation: spending change targets protected category '%s' for event %s",
                        ev.category(), sc.eventId()));
            }
            if (sc.type() == SpendingChangeType.stop) {
                if (!profile.isCategoryWillingToStop(ev.category())) {
                    violations.add(String.format("Rule 13 Violation: user is not willing to stop category '%s'", ev.category()));
                }
                if (ev.flexibility() != EventFlexibility.stoppable && ev.flexibility() != EventFlexibility.reducible_or_stoppable) {
                    violations.add("Rule 13 Violation: event is not stoppable: " + sc.eventId());
                }
            } else if (sc.type() == SpendingChangeType.reduce_to) {
                if (!profile.isCategoryWillingToReduce(ev.category())) {
                    violations.add(String.format("Rule 13 Violation: user is not willing to reduce category '%s'", ev.category()));
                }
                if (ev.flexibility() != EventFlexibility.reducible && ev.flexibility() != EventFlexibility.reducible_or_stoppable) {
                    violations.add("Rule 13 Violation: event is not reducible: " + sc.eventId());
                }
                if (sc.newAmount() == null || sc.newAmount().compareTo(BigDecimal.ZERO) < 0) {
                    violations.add("Rule 13 Violation: reduced amount cannot be negative: " + sc.newAmount());
                }
            }
        }

        // -------------------------------------------------------------------------
        // Rule 14: maximum three spending changes.
        // -------------------------------------------------------------------------
        if (spendingChanges.size() > 3) {
            violations.add("Rule 14 Violation: maximum 3 spending changes allowed, found: " + spendingChanges.size());
        }

        // -------------------------------------------------------------------------
        // Rule 15: stop/reduce conflict is impossible.
        // -------------------------------------------------------------------------
        long uniqueEventCount = spendingChanges.stream().map(SpendingChange::eventId).distinct().count();
        if (uniqueEventCount != spendingChanges.size()) {
            violations.add("Rule 15 Violation: same event cannot appear multiple times in spending changes");
        }

        // -------------------------------------------------------------------------
        // Rule 16: no unsupported financial information is introduced.
        // -------------------------------------------------------------------------
        if (method == PaymentMethod.installments && plan.paymentOptionId() != null) {
            if (findOptionById(financialState, plan.paymentOptionId()) == null) {
                violations.add("Rule 16 Violation: unsupported payment_option_id introduced: " + plan.paymentOptionId());
            }
        }
        for (SpendingChange sc : spendingChanges) {
            if (financialState.getEventById(sc.eventId()) == null) {
                violations.add("Rule 16 Violation: unsupported event_id introduced in spending changes: " + sc.eventId());
            }
        }

        // -------------------------------------------------------------------------
        // Rule 6 & 7: every payment is financially feasible & balance never falls below minimum_balance_to_keep.
        // -------------------------------------------------------------------------
        Set<String> stopped = new HashSet<>();
        Map<String, BigDecimal> reduced = new HashMap<>();
        for (SpendingChange sc : spendingChanges) {
            if (sc.type() == SpendingChangeType.stop) {
                stopped.add(sc.eventId());
            } else if (sc.type() == SpendingChangeType.reduce_to) {
                reduced.put(sc.eventId(), sc.newAmount());
            }
        }

        ForecastResult simResult = forecastEngine.simulate(financialState, payments, stopped, reduced);
        if (simResult.minimumBalanceViolated()) {
            violations.add(String.format("Rule 7 Violation: balance fell to %s on %s, violating minimum_balance_to_keep (%s)",
                    simResult.minimumBalanceObserved(), simResult.dateOfMinimumBalance(), financialState.minimumBalanceToKeep()));
        }
        if (!simResult.allPaymentsAffordable()) {
            violations.add("Rule 6 Violation: one or more payments in the proposed plan are not financially feasible");
        }

        if (violations.isEmpty()) {
            return VerificationResult.ok();
        } else {
            log.warn("Plan verification failed with {} violations: {}", violations.size(), violations);
            return VerificationResult.invalid(violations);
        }
    }

    @Override
    public VerificationResult verifyPrediction(Prediction prediction, FinancialState financialState) {
        if (prediction == null) {
            return VerificationResult.invalid("Prediction is null");
        }
        if (financialState == null) {
            return VerificationResult.invalid("FinancialState is null");
        }

        List<String> violations = new ArrayList<>();
        Request request = financialState.request();
        FinancialProfile profile = financialState.profile();

        // 1. Validate request ID matches
        if (!prediction.requestId().equals(request.requestId())) {
            violations.add(String.format("Prediction request_id (%s) does not match context request_id (%s)",
                    prediction.requestId(), request.requestId()));
        }

        // 2. Validate amount_safe_to_pay bounds
        BigDecimal safeToPay = prediction.amountSafeToPay();
        if (safeToPay == null || safeToPay.compareTo(BigDecimal.ZERO) < 0 || safeToPay.compareTo(request.requestedAmount()) > 0) {
            violations.add(String.format("Rule 1 Violation: amount_safe_to_pay (%s) outside [0, %s]",
                    safeToPay, request.requestedAmount()));
        }

        // 3. Status consistency checks
        AffordabilityStatus status = prediction.affordabilityStatus();
        PaymentMethod method = prediction.recommendedPaymentMethod();
        LocalDate earliestFullDate = prediction.earliestDateForFullPayment();

        if (status == AffordabilityStatus.affordable_now) {
            if (earliestFullDate == null || !earliestFullDate.equals(request.requestDate())) {
                violations.add("affordable_now requires earliest_date_for_full_payment to equal request_date");
            }
            if (!profile.considersMethod(PaymentMethod.full_payment)) {
                violations.add("affordable_now requires user to consider full_payment");
            }
            if (method != PaymentMethod.full_payment) {
                violations.add("affordable_now requires recommended_payment_method to be full_payment");
            }
        }

        if (status == AffordabilityStatus.not_affordable) {
            if (method != PaymentMethod.not_recommended) {
                violations.add("not_affordable requires recommended_payment_method to be not_recommended");
            }
            if (prediction.paymentPlan() != null && !prediction.paymentPlan().isEmpty()) {
                violations.add("not_affordable requires payment_plan to be empty");
            }
        }

        // 4. Use typed payments and spending changes directly
        List<Payment> parsedPayments = prediction.paymentPlan() != null ? prediction.paymentPlan() : Collections.emptyList();
        List<SpendingChange> parsedChanges = prediction.spendingChangesNeeded() != null ? prediction.spendingChangesNeeded() : Collections.emptyList();

        Plan reconstructedPlan = new Plan(
                method,
                parsedPayments,
                calculateTotal(parsedPayments),
                parsedPayments.isEmpty() ? null : parsedPayments.get(parsedPayments.size() - 1).paymentDate(),
                parsedPayments.size(),
                null,
                parsedChanges
        );

        VerificationResult planResult = verifyPlan(reconstructedPlan, safeToPay, earliestFullDate, financialState);
        if (!planResult.valid()) {
            violations.addAll(planResult.violations());
        }

        return violations.isEmpty() ? VerificationResult.ok() : VerificationResult.invalid(violations);
    }

    private List<Payment> parsePaymentPlan(String planStr) {
        if (planStr == null || planStr.isBlank() || "none".equalsIgnoreCase(planStr.trim())) {
            return Collections.emptyList();
        }
        List<Payment> list = new ArrayList<>();
        String[] entries = planStr.split("\\|");
        for (String entry : entries) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2) {
                LocalDate date = LocalDate.parse(parts[0].trim());
                BigDecimal amount = new BigDecimal(parts[1].trim());
                list.add(new Payment(date, amount));
            }
        }
        return list;
    }

    private List<SpendingChange> parseSpendingChanges(String changesStr) {
        if (changesStr == null || changesStr.isBlank() || "none".equalsIgnoreCase(changesStr.trim())) {
            return Collections.emptyList();
        }
        List<SpendingChange> list = new ArrayList<>();
        String[] entries = changesStr.split("\\|");
        for (String entry : entries) {
            String[] parts = entry.trim().split(":");
            if (parts.length >= 2) {
                if ("stop".equalsIgnoreCase(parts[0].trim())) {
                    list.add(SpendingChange.stop(parts[1].trim()));
                } else if ("reduce_to".equalsIgnoreCase(parts[0].trim()) && parts.length == 3) {
                    list.add(SpendingChange.reduceTo(parts[1].trim(), new BigDecimal(parts[2].trim())));
                }
            }
        }
        return list;
    }

    private PaymentOption findOptionById(FinancialState state, String optionId) {
        if (optionId == null || state.availablePaymentOptions() == null) {
            return null;
        }
        return state.availablePaymentOptions().stream()
                .filter(o -> o.paymentOptionId().equals(optionId))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateTotal(List<Payment> payments) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Payment p : payments) {
            sum = sum.add(p.amount());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}

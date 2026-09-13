package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PlanVerifierTest {

    @Autowired
    private PlanVerifier planVerifier;

    @Autowired
    private FinancialStateService financialStateService;

    @Test
    @DisplayName("Rule 1: amount_safe_to_pay must be between 0 and requested_amount")
    void testRule1AmountSafeToPayBounds() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        BigDecimal requested = state.request().requestedAmount();
        Plan fullPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), requested)),
                requested,
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        // Negative
        VerificationResult resNeg = planVerifier.verifyPlan(fullPlan, new BigDecimal("-10.00"), state.request().requestDate(), state);
        assertFalse(resNeg.valid());
        assertTrue(resNeg.violations().stream().anyMatch(v -> v.contains("Rule 1 Violation")));

        // Above requested
        VerificationResult resAbove = planVerifier.verifyPlan(fullPlan, requested.add(BigDecimal.TEN), state.request().requestDate(), state);
        assertFalse(resAbove.valid());
        assertTrue(resAbove.violations().stream().anyMatch(v -> v.contains("Rule 1 Violation")));

        // Valid boundary values
        assertTrue(planVerifier.verifyPlan(fullPlan, requested, state.request().requestDate(), state).valid());
        assertTrue(planVerifier.verifyPlan(fullPlan, BigDecimal.ZERO, state.request().requestDate(), state).valid());
    }

    @Test
    @DisplayName("Rule 2: Payment dates must be strictly chronological")
    void testRule2ChronologicalDates() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan outOfOrderPlan = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(LocalDate.of(2024, 4, 1), new BigDecimal("500.00")),
                        new Payment(LocalDate.of(2024, 3, 1), new BigDecimal("500.00"))
                ),
                new BigDecimal("1000.00"),
                LocalDate.of(2024, 4, 1),
                2,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(outOfOrderPlan, BigDecimal.ZERO, null, state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 2 Violation")));
    }

    @Test
    @DisplayName("Rule 3: Every payment amount must be positive")
    void testRule3PositivePaymentAmounts() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan zeroPaymentPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), BigDecimal.ZERO)),
                BigDecimal.ZERO,
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(zeroPaymentPlan, BigDecimal.ZERO, state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 3 Violation")));
    }

    @Test
    @DisplayName("Rule 4: Sum of payments must equal requested_amount for full payment")
    void testRule4SumOfPayments() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        // Pays 500 when requested is 25256
        Plan partialUnderpaidPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), new BigDecimal("500.00"))),
                new BigDecimal("500.00"),
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(partialUnderpaidPlan, new BigDecimal("500.00"), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 4 Violation")));
    }

    @Test
    @DisplayName("Rule 5: Plan finishes by desired_completion_date")
    void testRule5FinishesByDeadline() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        LocalDate deadline = state.request().desiredCompletionDate();
        LocalDate lateDate = deadline.plusDays(10);

        Plan latePlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(lateDate, state.request().requestedAmount())),
                state.request().requestedAmount(),
                lateDate,
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(latePlan, BigDecimal.ZERO, lateDate, state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 5 Violation")));
    }

    @Test
    @DisplayName("Rule 6 & 7: Financially feasible and balance never falls below minimum_balance_to_keep")
    void testRule6And7FinancialSafety() {
        // request_06: requested 620.40, but safeToPay is only 549.61 without spending changes
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        // Immediate full payment without any spending changes violates minimum balance
        Plan unsafeFullPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(unsafeFullPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 7 Violation")));
    }

    @Test
    @DisplayName("Rule 8: Installment schedules must match supplied option")
    void testRule8InstallmentsMatchOption() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan fakeOptionPlan = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(state.request().requestDate(), new BigDecimal("1000.00")),
                        new Payment(state.request().requestDate().plusDays(30), new BigDecimal("1000.00"))
                ),
                new BigDecimal("2000.00"),
                state.request().requestDate().plusDays(30),
                2,
                "opt_fake_nonexistent",
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(fakeOptionPlan, BigDecimal.ZERO, null, state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 8 Violation")));
    }

    @Test
    @DisplayName("Rule 9: Partial payment must contain exactly two payments")
    void testRule9PartialPaymentTwoPayments() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan threePaymentPartial = new Plan(
                PaymentMethod.partial_payment,
                List.of(
                        new Payment(state.request().requestDate(), new BigDecimal("100.00")),
                        new Payment(state.request().requestDate().plusDays(10), new BigDecimal("100.00")),
                        new Payment(state.request().requestDate().plusDays(20), new BigDecimal("100.00"))
                ),
                new BigDecimal("300.00"),
                state.request().requestDate().plusDays(20),
                3,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(threePaymentPartial, new BigDecimal("100.00"), state.request().requestDate().plusDays(20), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 9 Violation")));
    }

    @Test
    @DisplayName("Rule 10: Partial payment eligibility rules enforced")
    void testRule10PartialPaymentEligibility() {
        // request_01: user_01 does not consider partial_payment or does not allow partial payment
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan partialPlan = new Plan(
                PaymentMethod.partial_payment,
                List.of(
                        new Payment(state.request().requestDate(), new BigDecimal("1000.00")),
                        new Payment(state.request().requestDate().plusDays(30), state.request().requestedAmount().subtract(new BigDecimal("1000.00")))
                ),
                state.request().requestedAmount(),
                state.request().requestDate().plusDays(30),
                2,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(partialPlan, new BigDecimal("1000.00"), state.request().requestDate().plusDays(30), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 10 Violation")));
    }

    @Test
    @DisplayName("Rule 11: Immediate payment methods must be accepted by user")
    void testRule11ImmediatePaymentMethodAccepted() {
        // user_01 does not consider installments
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan installmentPlan = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(state.request().requestDate(), new BigDecimal("500.00")),
                        new Payment(state.request().requestDate().plusDays(30), new BigDecimal("500.00"))
                ),
                new BigDecimal("1000.00"),
                state.request().requestDate().plusDays(30),
                2,
                "opt_fake",
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(installmentPlan, BigDecimal.ZERO, null, state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 11 Violation")));
    }

    @Test
    @DisplayName("Rule 12: Wait plan is only valid when full payment becomes safe strictly later")
    void testRule12WaitPlanRules() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        // Wait plan proposed on request_date itself (invalid, should be full_payment)
        Plan waitTodayPlan = new Plan(
                PaymentMethod.wait,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(waitTodayPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 12 Violation")));
    }

    @Test
    @DisplayName("Rule 13: Spending changes target only flexible recurring expenses")
    void testRule13FlexibleSpendingOnly() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        // Attempting to stop an unknown or non-flexible event
        Plan invalidSpendingPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                null,
                List.of(SpendingChange.stop("event_nonexistent_999"))
        );

        VerificationResult res = planVerifier.verifyPlan(invalidSpendingPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 13 Violation")));
    }

    @Test
    @DisplayName("Rule 14: Maximum three spending changes allowed")
    void testRule14MaxThreeChanges() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        Plan fourChangesPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                null,
                List.of(
                        SpendingChange.stop("e1"),
                        SpendingChange.stop("e2"),
                        SpendingChange.stop("e3"),
                        SpendingChange.stop("e4")
                )
        );

        VerificationResult res = planVerifier.verifyPlan(fourChangesPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 14 Violation")));
    }

    @Test
    @DisplayName("Rule 15: Same event cannot be both stopped and reduced")
    void testRule15StopReduceConflict() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        Plan conflictPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                null,
                List.of(
                        SpendingChange.stop("event_476"),
                        SpendingChange.reduceTo("event_476", new BigDecimal("10.00"))
                )
        );

        VerificationResult res = planVerifier.verifyPlan(conflictPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 15 Violation")));
    }

    @Test
    @DisplayName("Rule 16: No unsupported financial information introduced")
    void testRule16NoUnsupportedInfo() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Plan ghostOptionPlan = new Plan(
                PaymentMethod.installments,
                List.of(new Payment(state.request().requestDate(), state.request().requestedAmount())),
                state.request().requestedAmount(),
                state.request().requestDate(),
                1,
                "opt_hallucinated_12345",
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(ghostOptionPlan, state.request().requestedAmount(), state.request().requestDate(), state);
        assertFalse(res.valid());
        assertTrue(res.violations().stream().anyMatch(v -> v.contains("Rule 16 Violation")));
    }

    @Test
    @DisplayName("Valid Plan Verification: request_01 full payment passes all 16 rules")
    void testValidPlanVerification() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        BigDecimal amount = state.request().requestedAmount();
        Plan validFullPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), amount)),
                amount,
                state.request().requestDate(),
                1,
                null,
                Collections.emptyList()
        );

        VerificationResult res = planVerifier.verifyPlan(validFullPlan, amount, state.request().requestDate(), state);
        assertTrue(res.valid(), "request_01 full plan must pass verification: " + res.violations());
        assertTrue(res.violations().isEmpty());
    }

    @Test
    @DisplayName("Prediction Verification: End-to-end output row validation")
    void testPredictionVerification() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        BigDecimal amount = state.request().requestedAmount();
        Prediction validPrediction = new Prediction(
                "request_01",
                amount,
                AffordabilityStatus.affordable_now,
                PaymentMethod.full_payment,
                List.of(new Payment(state.request().requestDate(), amount)),
                state.request().requestDate(),
                Collections.emptyList(),
                "Safe for immediate full payment."
        );

        VerificationResult res = planVerifier.verifyPrediction(validPrediction, state);
        assertTrue(res.valid(), "Valid prediction must pass verification: " + res.violations());

        // Contradictory prediction: status is affordable_now but method is not_recommended
        Prediction contradictory = new Prediction(
                "request_01",
                amount,
                AffordabilityStatus.affordable_now,
                PaymentMethod.not_recommended,
                Collections.emptyList(),
                state.request().requestDate(),
                Collections.emptyList(),
                "Contradiction"
        );

        VerificationResult resBad = planVerifier.verifyPrediction(contradictory, state);
        assertFalse(resBad.valid());
    }
}

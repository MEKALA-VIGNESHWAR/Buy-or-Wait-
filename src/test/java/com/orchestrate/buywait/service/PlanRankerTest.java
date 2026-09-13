package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.Payment;
import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.model.Plan;
import com.orchestrate.buywait.model.SpendingChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlanRankerTest {

    private PlanRanker planRanker;

    @BeforeEach
    void setUp() {
        planRanker = new PlanRankerImpl();
    }

    @Test
    @DisplayName("1. Criterion 2 vs 3: Installments cost more but avoid spending changes")
    void testInstallmentsCostMoreButAvoidSpendingChanges() {
        LocalDate requestDate = LocalDate.of(2026, 1, 1);
        LocalDate deadline = LocalDate.of(2026, 4, 1);

        // Plan A: Installments costing $1050 (with $50 interest/fee), but requiring NO spending changes
        Plan installmentsNoChanges = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("350.00")),
                        new Payment(LocalDate.of(2026, 2, 1), new BigDecimal("350.00")),
                        new Payment(LocalDate.of(2026, 3, 1), new BigDecimal("350.00"))
                ),
                new BigDecimal("1050.00"),
                LocalDate.of(2026, 3, 1),
                3,
                "opt_inst_fee",
                Collections.emptyList() // No spending changes
        );

        // Plan B: Full payment costing $1000, but requiring a spending cut
        Plan fullPaymentWithSpendingChange = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(requestDate, new BigDecimal("1000.00"))),
                new BigDecimal("1000.00"),
                requestDate,
                1,
                "opt_full",
                List.of(SpendingChange.stop("event_flexible_sub")) // Requires spending change
        );

        // Criterion 1: Both complete before deadline (2026-03-01 <= 2026-04-01 and 2026-01-01 <= 2026-04-01)
        // Criterion 2: "Require no spending changes" takes precedence over Criterion 3 ("Minimize total amount paid")
        List<Plan> ranked = planRanker.rankPlans(List.of(fullPaymentWithSpendingChange, installmentsNoChanges), deadline);

        assertEquals(2, ranked.size());
        assertEquals(installmentsNoChanges, ranked.get(0),
                "Plan avoiding spending changes must rank higher even if total cost is higher");
        assertEquals(fullPaymentWithSpendingChange, ranked.get(1));
    }

    @Test
    @DisplayName("2. Criterion 4: Partial payment starts earlier than waiting")
    void testPartialPaymentStartsEarlier() {
        LocalDate requestDate = LocalDate.of(2026, 1, 1);
        LocalDate deadline = LocalDate.of(2026, 2, 1);
        BigDecimal totalAmount = new BigDecimal("1000.00");

        // Plan A: Partial payment starting on request_date (2026-01-01), completing 2026-01-15
        Plan partialPlan = new Plan(
                PaymentMethod.partial_payment,
                List.of(
                        new Payment(requestDate, new BigDecimal("400.00")),
                        new Payment(LocalDate.of(2026, 1, 15), new BigDecimal("600.00"))
                ),
                totalAmount,
                LocalDate.of(2026, 1, 15),
                2,
                null,
                Collections.emptyList()
        );

        // Plan B: Wait plan paying full amount on 2026-01-15
        Plan waitPlan = new Plan(
                PaymentMethod.wait,
                List.of(new Payment(LocalDate.of(2026, 1, 15), totalAmount)),
                totalAmount,
                LocalDate.of(2026, 1, 15),
                1, // Wait has 1 payment, but starts LATER
                null,
                Collections.emptyList()
        );

        // Criterion 1: Both complete on time (2026-01-15 <= 2026-02-01) -> tie
        // Criterion 2: Both require 0 spending changes -> tie
        // Criterion 3: Both have totalAmount = 1000.00 -> tie
        // Criterion 4: "Start payment earlier" -> Partial payment starts 2026-01-01, Wait starts 2026-01-15
        // Criterion 4 takes precedence over Criterion 5 ("Use fewer payments")
        List<Plan> ranked = planRanker.rankPlans(List.of(waitPlan, partialPlan), deadline);

        assertEquals(2, ranked.size());
        assertEquals(partialPlan, ranked.get(0),
                "Partial payment must rank higher because it starts earlier (Criterion 4 before Criterion 5)");
        assertEquals(waitPlan, ranked.get(1));
    }

    @Test
    @DisplayName("3. Criterion 5: One plan uses fewer payments")
    void testOnePlanUsesFewerPayments() {
        LocalDate requestDate = LocalDate.of(2026, 1, 1);
        LocalDate deadline = LocalDate.of(2026, 3, 1);
        BigDecimal amount = new BigDecimal("1200.00");

        // Plan A: Immediate full payment (1 payment)
        Plan fullPayment = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(requestDate, amount)),
                amount,
                requestDate,
                1,
                "opt_full",
                Collections.emptyList()
        );

        // Plan B: 0% interest 3-installment plan (3 payments, starts on request_date)
        Plan zeroInterestInstallments = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(requestDate, new BigDecimal("400.00")),
                        new Payment(LocalDate.of(2026, 1, 15), new BigDecimal("400.00")),
                        new Payment(LocalDate.of(2026, 2, 1), new BigDecimal("400.00"))
                ),
                amount,
                LocalDate.of(2026, 2, 1),
                3,
                "opt_inst_free",
                Collections.emptyList()
        );

        // Criterion 1: Both complete by deadline -> tie
        // Criterion 2: Both require no spending changes -> tie
        // Criterion 3: Both cost exactly 1200.00 -> tie
        // Criterion 4: Both start on 2026-01-01 -> tie
        // Criterion 5: "Use fewer payments" -> 1 payment vs 3 payments
        List<Plan> ranked = planRanker.rankPlans(List.of(zeroInterestInstallments, fullPayment), deadline);

        assertEquals(2, ranked.size());
        assertEquals(fullPayment, ranked.get(0),
                "Plan with fewer payments must rank higher when start date and cost are equal");
        assertEquals(zeroInterestInstallments, ranked.get(1));
    }

    @Test
    @DisplayName("4. Criterion 6: Two plans tie and payment_option_id breaks the tie")
    void testTwoPlansTieAndPaymentOptionIdBreaksTie() {
        LocalDate requestDate = LocalDate.of(2026, 1, 1);
        LocalDate deadline = LocalDate.of(2026, 4, 1);
        BigDecimal amount = new BigDecimal("600.00");

        List<Payment> payments = List.of(
                new Payment(requestDate, new BigDecimal("300.00")),
                new Payment(LocalDate.of(2026, 2, 1), new BigDecimal("300.00"))
        );

        // Plan A: Option opt_01
        Plan planOption1 = new Plan(
                PaymentMethod.installments,
                payments,
                amount,
                LocalDate.of(2026, 2, 1),
                2,
                "opt_01",
                Collections.emptyList()
        );

        // Plan B: Option opt_02 with identical terms
        Plan planOption2 = new Plan(
                PaymentMethod.installments,
                payments,
                amount,
                LocalDate.of(2026, 2, 1),
                2,
                "opt_02",
                Collections.emptyList()
        );

        // Criteria 1-5 all tie.
        // Criterion 6: "Lowest payment_option_id as final tie-breaker" -> "opt_01" < "opt_02"
        List<Plan> ranked = planRanker.rankPlans(List.of(planOption2, planOption1), deadline);

        assertEquals(2, ranked.size());
        assertEquals("opt_01", ranked.get(0).paymentOptionId(),
                "Option opt_01 must win over opt_02 due to lower payment_option_id");
        assertEquals("opt_02", ranked.get(1).paymentOptionId());
    }

    @Test
    @DisplayName("5. Criterion 1: Plan completing by deadline beats plan completing late regardless of cost")
    void testDeadlinePrecedenceOverCostAndChanges() {
        LocalDate requestDate = LocalDate.of(2026, 1, 1);
        LocalDate deadline = LocalDate.of(2026, 2, 1);

        // Plan A: Completes on time (2026-01-20 <= 2026-02-01), but costs $1500 and requires spending changes
        Plan onTimePlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 20), new BigDecimal("1500.00"))),
                new BigDecimal("1500.00"),
                LocalDate.of(2026, 1, 20),
                1,
                "opt_ontime",
                List.of(SpendingChange.stop("event_sub"))
        );

        // Plan B: Completes late (2026-03-01 > 2026-02-01), but costs only $1000 and requires no changes
        Plan latePlan = new Plan(
                PaymentMethod.installments,
                List.of(
                        new Payment(requestDate, new BigDecimal("500.00")),
                        new Payment(LocalDate.of(2026, 3, 1), new BigDecimal("500.00"))
                ),
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 3, 1),
                2,
                "opt_late",
                Collections.emptyList()
        );

        // Criterion 1: Complete by deadline takes strict precedence
        List<Plan> ranked = planRanker.rankPlans(List.of(latePlan, onTimePlan), deadline);

        assertEquals(2, ranked.size());
        assertEquals(onTimePlan, ranked.get(0), "On-time plan must take precedence over late plan");
        assertEquals(latePlan, ranked.get(1));
    }

    @Test
    @DisplayName("6. Safety Filtering: Ineligible or not_recommended plans are filtered out before ranking")
    void testIneligiblePlansFilteredOut() {
        LocalDate deadline = LocalDate.of(2026, 2, 1);

        Plan validPlan = new Plan(
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("500.00"))),
                new BigDecimal("500.00"),
                LocalDate.of(2026, 1, 1),
                1,
                "opt_valid",
                Collections.emptyList()
        );

        Plan notRecommended = Plan.notRecommended();
        Plan emptyPayments = new Plan(
                PaymentMethod.full_payment,
                Collections.emptyList(),
                new BigDecimal("500.00"),
                null,
                0,
                null,
                Collections.emptyList()
        );

        List<Plan> ranked = planRanker.rankPlans(List.of(notRecommended, validPlan, emptyPayments), deadline);

        assertEquals(1, ranked.size());
        assertEquals(validPlan, ranked.get(0));

        Optional<Plan> best = planRanker.selectBestPlan(List.of(notRecommended, emptyPayments), deadline);
        assertTrue(best.isEmpty(), "selectBestPlan must be empty when no valid candidate plans exist");
    }
}

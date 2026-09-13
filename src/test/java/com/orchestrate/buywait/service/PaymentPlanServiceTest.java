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
class PaymentPlanServiceTest {

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private FinancialStateService financialStateService;

    @Test
    @DisplayName("1. Full Payment Candidate: Generates 1-payment plan on request_date when safe")
    void testFullPaymentCandidate() {
        // request_01: user_01 considers full_payment and it is safe immediately
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        List<Plan> candidates = paymentPlanService.generateCandidatePlans(state);
        assertFalse(candidates.isEmpty());

        boolean hasFullPayment = candidates.stream()
                .anyMatch(p -> p.paymentMethod() == PaymentMethod.full_payment
                        && p.paymentCount() == 1
                        && p.completionDate().equals(state.request().requestDate())
                        && p.totalAmount().compareTo(state.request().requestedAmount()) == 0);

        assertTrue(hasFullPayment, "Should generate a valid immediate full_payment plan");
    }

    @Test
    @DisplayName("2. Partial Payment Candidate: Generates exactly 2 payments totaling requested amount")
    void testPartialPaymentCandidate() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        LocalDate compDate = reqDate.plusDays(30);
        Request request = new Request(
                "req_part", "user_part", reqDate, RequestType.purchase,
                new BigDecimal("1000.00"), compDate, true, "Camera" // allows_partial_payment = true
        );
        FinancialProfile profile = new FinancialProfile(
                "user_part", "USD", new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(PaymentMethod.partial_payment, PaymentMethod.full_payment), 12
        );
        // Initial buffer is 1500 - 1000 = 500.
        // Scheduled salary of 2000 arriving on 2024-01-15 (day 14)
        LocalDate salaryDate = reqDate.plusDays(14);
        FinancialEvent salary = new FinancialEvent(
                "ev_sal", "user_part", "income", "Payroll", "salary",
                EventDirection.credit, new BigDecimal("2000.00"), "USD",
                salaryDate, salaryDate, EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(salary), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        List<Plan> candidates = paymentPlanService.generateCandidatePlans(state);
        assertFalse(candidates.isEmpty());

        Plan partial = candidates.stream()
                .filter(p -> p.paymentMethod() == PaymentMethod.partial_payment)
                .findFirst()
                .orElse(null);

        assertNotNull(partial, "Expected a partial_payment candidate plan");
        assertEquals(2, partial.paymentCount());
        assertEquals(reqDate, partial.payments().get(0).paymentDate());
        assertEquals(new BigDecimal("500.00"), partial.payments().get(0).amount());
        assertEquals(salaryDate, partial.payments().get(1).paymentDate());
        assertEquals(new BigDecimal("500.00"), partial.payments().get(1).amount());
        assertEquals(new BigDecimal("1000.00"), partial.totalAmount());
        assertEquals(salaryDate, partial.completionDate());
    }

    @Test
    @DisplayName("3. Installments Candidate: Follows supplied payment option and respects max_installment_months")
    void testInstallmentsCandidate() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_inst", "user_inst", reqDate, RequestType.purchase,
                new BigDecimal("600.00"), reqDate.plusDays(90), false, "Laptop"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_inst", "USD", new BigDecimal("1200.00"), new BigDecimal("500.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(PaymentMethod.installments), 3 // max 3 months
        );

        // Valid option: 3 payments of 200 every 30 days
        PaymentOption validOpt = new PaymentOption(
                "opt_3m", "req_inst", PaymentMethod.installments,
                new BigDecimal("200.00"), 3, reqDate, 30, BigDecimal.ZERO, new BigDecimal("600.00")
        );

        // Invalid option: 6 payments (exceeds max 3 months)
        PaymentOption invalidOpt = new PaymentOption(
                "opt_6m", "req_inst", PaymentMethod.installments,
                new BigDecimal("105.00"), 6, reqDate, 30, new BigDecimal("30.00"), new BigDecimal("630.00")
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1200.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(validOpt, invalidOpt), Collections.emptyList(), Collections.emptyList()
        );

        List<Plan> candidates = paymentPlanService.generateCandidatePlans(state);
        assertFalse(candidates.isEmpty());

        // Must include validOpt
        boolean hasValid = candidates.stream().anyMatch(p -> "opt_3m".equals(p.paymentOptionId()));
        assertTrue(hasValid, "Must generate plan for valid installment option");

        // Must NOT include invalidOpt
        boolean hasInvalid = candidates.stream().anyMatch(p -> "opt_6m".equals(p.paymentOptionId()));
        assertFalse(hasInvalid, "Must reject option exceeding user maxInstallmentMonths");
    }

    @Test
    @DisplayName("4. Wait Candidate: Generated when full payment becomes safe later before deadline")
    void testWaitCandidate() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_wait", "user_wait", reqDate, RequestType.purchase,
                new BigDecimal("1000.00"), reqDate.plusDays(30), false, "Equipment"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_wait", "USD", new BigDecimal("1200.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(PaymentMethod.full_payment), 12
        );
        // Current buffer is only 200, so not safe today.
        // Scheduled salary of 2500 on 2024-01-15 (day 14)
        LocalDate salaryDate = reqDate.plusDays(14);
        FinancialEvent salary = new FinancialEvent(
                "ev_sal", "user_wait", "income", "Payroll", "salary",
                EventDirection.credit, new BigDecimal("2500.00"), "USD",
                salaryDate, salaryDate, EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1200.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(salary), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        List<Plan> candidates = paymentPlanService.generateCandidatePlans(state);
        assertFalse(candidates.isEmpty());

        Plan waitPlan = candidates.stream()
                .filter(p -> p.paymentMethod() == PaymentMethod.wait)
                .findFirst()
                .orElse(null);

        assertNotNull(waitPlan, "Expected a wait candidate plan");
        assertEquals(1, waitPlan.paymentCount());
        assertEquals(salaryDate, waitPlan.completionDate());
        assertEquals(new BigDecimal("1000.00"), waitPlan.totalAmount());
    }

    @Test
    @DisplayName("5. Spending-Change-Assisted Plan: Generates plan when stopping a flexible expense enables safety")
    void testSpendingChangeAssistedPlan() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        List<Plan> candidates = paymentPlanService.generateCandidatePlans(state);
        assertFalse(candidates.isEmpty());

        boolean hasSpendingChangePlan = candidates.stream()
                .anyMatch(Plan::requiresSpendingChanges);

        assertTrue(hasSpendingChangePlan, "Expected candidate plan assisted by spending changes for request_06");
    }
}

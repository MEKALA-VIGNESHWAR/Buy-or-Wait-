package com.orchestrate.buywait.engine;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.service.FinancialStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ForecastSimulationEngineTest {

    @Autowired
    private ForecastSimulationEngine engine;

    @Autowired
    private FinancialStateService financialStateService;

    @Test
    @DisplayName("1. 90-Day Horizon: Exactly 91 days simulated from request_date to request_date + 90")
    void testForecastHorizon() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        ForecastResult result = engine.simulate(state, Collections.emptyList());
        assertNotNull(result);

        assertEquals(state.request().requestDate(), result.startDate());
        assertEquals(state.request().requestDate().plusDays(90), result.endDate());
        assertEquals(91, result.dailyForecasts().size());
    }

    @Test
    @DisplayName("2. Daily Formula: closing = opening + income - mandatory - other - payments")
    void testDailyBalanceFormula() {
        FinancialState state = financialStateService.reconstructState("request_01");
        List<Payment> plan = List.of(
                new Payment(state.request().requestDate(), new BigDecimal("1000.00")),
                new Payment(state.request().requestDate().plusDays(10), new BigDecimal("500.00"))
        );

        ForecastResult result = engine.simulate(state, plan);

        BigDecimal previousClosing = null;
        for (ForecastDay day : result.dailyForecasts()) {
            if (previousClosing != null) {
                assertEquals(previousClosing, day.openingBalance(), "Opening balance must match previous day's closing balance on " + day.date());
            }
            BigDecimal expectedClosing = day.openingBalance()
                    .add(day.confirmedIncome())
                    .subtract(day.mandatoryExpenses())
                    .subtract(day.otherOutflows())
                    .subtract(day.proposedPayments());

            assertEquals(expectedClosing, day.closingBalance(), "Closing balance formula failed on " + day.date());
            previousClosing = day.closingBalance();
        }
    }

    @Test
    @DisplayName("3. isSafe: Plan exceeding budget is rejected due to minimum balance violation")
    void testUnsafePlanViolatingMinimumBalance() {
        FinancialState state = financialStateService.reconstructState("request_01");

        // Profile balance is ~58,481 ZAR, min balance is 18,000 ZAR.
        // Attempting to pay 50,000 ZAR immediately will drop balance below 18,000 ZAR.
        Payment excessivePayment = new Payment(state.request().requestDate(), new BigDecimal("50000.00"));
        // Request requestedAmount is 25256, but let's test a payment plan exceeding capacity
        List<Payment> plan = List.of(excessivePayment);

        boolean safe = engine.isSafe(plan, state);
        assertFalse(safe, "Plan driving balance below minimum balance must not be safe");
    }

    @Test
    @DisplayName("4. isSafe: Incomplete plan is rejected")
    void testIncompletePlanRejected() {
        FinancialState state = financialStateService.reconstructState("request_01");
        BigDecimal reqAmount = state.request().requestedAmount(); // 25256

        // Plan only pays 10,000 instead of 25,256
        List<Payment> partialPlan = List.of(new Payment(state.request().requestDate(), new BigDecimal("10000.00")));

        boolean safe = engine.isSafe(partialPlan, state);
        assertFalse(safe, "Incomplete plan that does not complete requested amount must not be safe");
    }

    @Test
    @DisplayName("5. isSafe: Plan paying past desired_completion_date is rejected")
    void testLatePlanRejected() {
        FinancialState state = financialStateService.reconstructState("request_01");
        LocalDate desired = state.request().desiredCompletionDate(); // 2024-03-20

        // Schedule payment on 2024-03-25 (after completion date)
        List<Payment> latePlan = List.of(new Payment(desired.plusDays(5), state.request().requestedAmount()));

        boolean safe = engine.isSafe(latePlan, state);
        assertFalse(safe, "Plan completing after desired completion date must not be safe");
    }

    @Test
    @DisplayName("6. calculateAmountSafeToPay: Returns safe amount within user's buffer capped at requested amount")
    void testCalculateAmountSafeToPay() {
        FinancialState state = financialStateService.reconstructState("request_01");

        BigDecimal safeAmount = engine.calculateAmountSafeToPay(state);
        assertNotNull(safeAmount);
        assertTrue(safeAmount.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(safeAmount.compareTo(state.request().requestedAmount()) <= 0);

        // For request_01, sample_requests specifies 25256.0 is safe to pay today
        assertEquals(new BigDecimal("25256.00"), safeAmount);
    }

    @Test
    @DisplayName("7. findEarliestDateForFullPayment: Finds valid earliest date for full payment")
    void testFindEarliestDateForFullPayment() {
        FinancialState state = financialStateService.reconstructState("request_01");

        Optional<LocalDate> earliest = engine.findEarliestDateForFullPayment(state, state.request().requestedAmount());
        assertTrue(earliest.isPresent());
        // For request_01, full payment is affordable on request_date (2024-03-03)
        assertEquals(state.request().requestDate(), earliest.get());
    }

    @Test
    @DisplayName("8. Spending Changes: Stopping a flexible expense improves cash buffer")
    void testSpendingChangesImproveBuffer() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        // Baseline without spending changes
        ForecastResult baseline = engine.simulate(state, Collections.emptyList());

        // Now simulate with event_476 stopped (family streaming plan)
        ForecastResult withStop = engine.simulate(state, Collections.emptyList(), Set.of("event_476"), Collections.emptyMap());

        // Balance with expense stopped should be strictly greater than or equal to baseline
        assertTrue(withStop.finalBalance().compareTo(baseline.finalBalance()) >= 0,
                "Stopping an expense must improve or preserve final balance");
    }
}

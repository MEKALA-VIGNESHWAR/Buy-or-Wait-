package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SpendingAdjustmentServiceTest {

    @Autowired
    private SpendingAdjustmentService spendingAdjustmentService;

    @Autowired
    private FinancialStateService financialStateService;

    @Autowired
    private com.orchestrate.buywait.engine.ForecastSimulationEngine forecastEngine;

    @Test
    @DisplayName("1. Single Stop Change: request_06 recommends stop:event_476 for full payment")
    void testSingleStopChangeRequest06() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        List<Payment> fullPlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));
        Optional<List<SpendingChange>> bestChanges = spendingAdjustmentService.findSpendingChangesToMakeSafe(fullPlan, state);
        assertTrue(bestChanges.isPresent(), "Spending changes should make request_06 affordable");

        List<SpendingChange> changes = bestChanges.get();
        assertEquals(1, changes.size(), "Should recommend exactly 1 spending change");

        SpendingChange c = changes.get(0);
        assertEquals(SpendingChangeType.stop, c.type());
        assertEquals("event_476", c.eventId());
        assertEquals("stop:event_476", c.toOutputFormat());
    }

    @Test
    @DisplayName("2. Single Reduce Change: request_11 recommends reduce_to:event_989:665950 for full payment")
    void testSingleReduceChangeRequest11() {
        FinancialState state = financialStateService.reconstructState("request_11");
        assertNotNull(state);

        List<Payment> fullPlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));
        Optional<List<SpendingChange>> bestChanges = spendingAdjustmentService.findSpendingChangesToMakeSafe(fullPlan, state);
        assertTrue(bestChanges.isPresent(), "Spending changes should make request_11 affordable");

        List<SpendingChange> changes = bestChanges.get();
        assertEquals(1, changes.size(), "Should recommend exactly 1 spending change");

        SpendingChange c = changes.get(0);
        assertEquals(SpendingChangeType.reduce_to, c.type());
        assertEquals("event_989", c.eventId());
        assertEquals("reduce_to:event_989:665950", c.toOutputFormat());
    }

    @Test
    @DisplayName("3. Multi-Change: request_21 recommends stop:event_1815 and reduce_to:event_1816:23.50")
    void testMultiChangeRequest21() {
        FinancialState state = financialStateService.reconstructState("request_21");
        assertNotNull(state);

        List<Payment> fullPlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));

        Optional<List<SpendingChange>> bestChanges = spendingAdjustmentService.findSpendingChangesToMakeSafe(fullPlan, state);
        assertTrue(bestChanges.isPresent(), "Spending changes should make request_21 affordable");

        List<SpendingChange> changes = bestChanges.get();
        assertEquals(2, changes.size(), "Should recommend 2 spending changes");

        List<String> formatted = changes.stream().map(SpendingChange::toOutputFormat).sorted().toList();
        assertTrue(formatted.contains("stop:event_1815"));
        assertTrue(formatted.contains("reduce_to:event_1816:23.50"));
    }

    @Test
    @DisplayName("4. Mutual Exclusivity: The same event cannot be both stopped and reduced in any candidate")
    void testMutualExclusivityRule() {
        FinancialState state = financialStateService.reconstructState("request_21");
        assertNotNull(state);

        List<Payment> fullPlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));
        List<List<SpendingChange>> allCandidates = spendingAdjustmentService.findRankedCandidateSpendingChanges(fullPlan, state);

        for (List<SpendingChange> combo : allCandidates) {
            long uniqueEventCount = combo.stream().map(SpendingChange::eventId).distinct().count();
            assertEquals(combo.size(), uniqueEventCount, "A combination must never contain duplicate event IDs: " + combo);
        }
    }

    @Test
    @DisplayName("5. Maximum of Three Changes: No candidate combination exceeds three changes")
    void testMaxThreeChangesRule() {
        FinancialState state = financialStateService.reconstructState("request_02");
        assertNotNull(state);

        List<Payment> fullPlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));
        List<List<SpendingChange>> allCandidates = spendingAdjustmentService.findRankedCandidateSpendingChanges(fullPlan, state);

        for (List<SpendingChange> combo : allCandidates) {
            assertTrue(combo.size() <= 3, "A candidate combination must have at most 3 changes: " + combo.size());
        }
    }

    @Test
    @DisplayName("6. Protected Category Immunity: Expenses in protected categories are never modified")
    void testProtectedCategoryImmunity() {
        FinancialState state = financialStateService.reconstructState("request_06");
        assertNotNull(state);

        FinancialProfile profile = state.profile();
        List<SpendingChange> eligible = spendingAdjustmentService.getEligibleCandidateChanges(state);

        for (SpendingChange sc : eligible) {
            FinancialEvent ev = state.getEventById(sc.eventId());
            if (ev != null) {
                assertFalse(profile.isCategoryProtected(ev.category()),
                        "Protected category " + ev.category() + " must never be in eligible spending changes");
            }
        }
    }

    @Test
    @DisplayName("7. Plan Already Safe: Returns empty spending changes when plan is already affordable")
    void testPlanAlreadySafe() {
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        // In request_01, requested_amount is 25256.00 and fully safe today
        List<Payment> safePlan = List.of(new Payment(state.request().requestDate(), state.request().requestedAmount()));

        Optional<List<SpendingChange>> changes = spendingAdjustmentService.findSpendingChangesToMakeSafe(safePlan, state);
        assertTrue(changes.isPresent());
        assertTrue(changes.get().isEmpty(), "No spending changes needed if plan is already safe");
    }
}

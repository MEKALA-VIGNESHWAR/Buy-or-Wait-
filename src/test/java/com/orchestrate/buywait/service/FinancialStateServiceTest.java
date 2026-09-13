package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinancialStateServiceTest {

    @Autowired
    private FinancialStateService financialStateService;

    @Autowired
    private FinancialDataRepository repository;

    @Autowired
    private FinancialEventNormalizer normalizer;

    @Test
    @DisplayName("1. Ignore Cancelled Transactions: Cancelled authorizations are ignored")
    void testIgnoreCancelledTransactions() {
        var eventOpt = repository.findEventById("event_100");
        assertTrue(eventOpt.isPresent(), "event_100 should exist in dataset");
        FinancialEvent cancelledAuth = eventOpt.get();

        assertEquals(EventStatus.cancelled, cancelledAuth.status());
        assertTrue(normalizer.isFailedOrCancelled(cancelledAuth));
    }

    @Test
    @DisplayName("2. Ignore Failed Transactions: Failed bill payments are ignored")
    void testIgnoreFailedTransactions() {
        var eventOpt = repository.findEventById("event_5168");
        assertTrue(eventOpt.isPresent(), "event_5168 should exist in dataset");
        FinancialEvent failedAttempt = eventOpt.get();

        assertEquals(EventStatus.failed, failedAttempt.status());
        assertTrue(normalizer.isFailedOrCancelled(failedAttempt));
    }

    @Test
    @DisplayName("3. Ignore Duplicate Records: 'Possible duplicate card charge' is ignored")
    void testIgnoreDuplicateCardCharges() {
        var eventOpt = repository.findEventById("event_12709");
        assertTrue(eventOpt.isPresent(), "event_12709 should exist in dataset");
        FinancialEvent duplicateCharge = eventOpt.get();

        assertTrue(normalizer.isDuplicate(duplicateCharge, null));
    }

    @Test
    @DisplayName("4. Ignore Pending Credits: Pending refunds must not be counted as cash")
    void testIgnorePendingCredits() {
        var eventOpt = repository.findEventById("event_1785");
        assertTrue(eventOpt.isPresent(), "event_1785 should exist in dataset");
        FinancialEvent pendingRefund = eventOpt.get();

        assertEquals(EventStatus.pending, pendingRefund.status());
        assertTrue(pendingRefund.isCredit());
        assertTrue(normalizer.isPendingCredit(pendingRefund), "Pending refund must be identified as pending credit to ignore");
    }

    @Test
    @DisplayName("5. Ignore Unrealized Investments: Portfolio valuation changes are not cash")
    void testIgnoreUnrealizedInvestments() {
        var eventOpt = repository.findEventById("event_1856");
        assertTrue(eventOpt.isPresent(), "event_1856 should exist in dataset");
        FinancialEvent portfolioValuation = eventOpt.get();

        assertEquals(EventStatus.unrealized, portfolioValuation.status());
        assertTrue(normalizer.isUnrealizedInvestment(portfolioValuation), "Unrealized portfolio valuation must not be treated as cash");
    }

    @Test
    @DisplayName("6. Reserve Pending Debits: Active pending debits are reserved and subtracted from buffer")
    void testReservePendingDebits() {
        // Request 01 (user_01, request_date: 2024-03-03) has pending debit event_102 (amount: 816.20 ZAR)
        FinancialState state = financialStateService.reconstructState("request_01");

        assertNotNull(state);
        assertTrue(state.hasPendingDebits(), "user_01 should have active pending debits");

        boolean hasEvent102 = state.pendingDebits().stream()
                .anyMatch(e -> "event_102".equals(e.eventId()));
        assertTrue(hasEvent102, "event_102 must be included in pendingDebits");

        assertTrue(state.reservedPendingDebits().compareTo(BigDecimal.ZERO) > 0,
                "reservedPendingDebits must be greater than zero");

        // Verify netImmediateBuffer = startingBalance - reservedPendingDebits - minimumBalanceToKeep
        BigDecimal expectedBuffer = state.startingBalance()
                .subtract(state.reservedPendingDebits())
                .subtract(state.minimumBalanceToKeep());
        assertEquals(0, expectedBuffer.compareTo(state.netImmediateBuffer()),
                "netImmediateBuffer must accurately reflect reserved pending debits");
    }

    @Test
    @DisplayName("7. Count Confirmed Salary: Scheduled salary is counted on settlement date")
    void testCountConfirmedSalary() {
        // Request 01 has confirmed salary event_103 scheduled for 2024-03-15
        FinancialState state = financialStateService.reconstructState("request_01");

        assertFalse(state.confirmedIncomeEvents().isEmpty(), "user_01 should have confirmed salary");
        boolean hasEvent103 = state.confirmedIncomeEvents().stream()
                .anyMatch(e -> "event_103".equals(e.eventId()));
        assertTrue(hasEvent103, "event_103 must be in confirmedIncomeEvents");
    }

    @Test
    @DisplayName("8. Distinguish Flexible vs Essential Recurring Expenses")
    void testFlexibleVsEssentialExpenses() {
        // Request 06 (user_06): user willing to stop streaming, event_476 is stoppable streaming subscription
        FinancialState state6 = financialStateService.reconstructState("request_06");

        // event_476 should be categorized under flexibleExpenses
        boolean hasStoppableStreaming = state6.flexibleExpenses().stream()
                .anyMatch(e -> "event_476".equals(e.eventId()));
        assertTrue(hasStoppableStreaming, "event_476 (streaming) must be classified under flexibleExpenses");

        // Request 04 (user_04): has confirmed scheduled future essential expense event_357 (Scheduled school fee)
        FinancialState state4 = financialStateService.reconstructState("request_04");
        boolean hasSchoolFee = state4.essentialExpenses().stream()
                .anyMatch(e -> "event_357".equals(e.eventId()));
        assertTrue(hasSchoolFee, "event_357 (fixed school fee) must be classified under essentialExpenses");
    }
}

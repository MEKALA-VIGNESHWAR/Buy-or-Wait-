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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EarliestFullPaymentServiceTest {

    @Autowired
    private EarliestFullPaymentService earliestService;

    @Autowired
    private FinancialStateService financialStateService;

    @Test
    @DisplayName("1. Safe Today: Returns request_date when full payment is affordable immediately")
    void testSafeToday() {
        // request_01: full payment of 25256.00 ZAR is safe on request_date (2024-03-03)
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        Optional<LocalDate> earliest = earliestService.findEarliestDateForFullPayment(state);
        assertTrue(earliest.isPresent());
        assertEquals(state.request().requestDate(), earliest.get());
    }

    @Test
    @DisplayName("2. Safe After Salary: Unsafe today, but becomes safe upon scheduled salary arrival")
    void testSafeAfterSalary() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_sal", "user_sal", reqDate, RequestType.purchase,
                new BigDecimal("1200.00"), reqDate.plusDays(30), true, "Electronics"
        );
        // Balance = 1500, min keep = 1000. Current buffer is only 500 (1200 is NOT safe today).
        FinancialProfile profile = new FinancialProfile(
                "user_sal", "USD", new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        // Salary of 2000 scheduled on 2024-01-15
        LocalDate salaryDate = reqDate.plusDays(14);
        FinancialEvent salary = new FinancialEvent(
                "ev_sal", "user_sal", "income", "Payroll", "salary",
                EventDirection.credit, new BigDecimal("2000.00"), "USD",
                salaryDate, salaryDate, EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(salary), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        Optional<LocalDate> earliest = earliestService.findEarliestDateForFullPayment(state);
        assertTrue(earliest.isPresent());
        // Must be safe on the salary settlement date (2024-01-15)
        assertEquals(salaryDate, earliest.get());
    }

    @Test
    @DisplayName("3. Safe After Recurring Income: Accumulates required funds after multiple income cycles")
    void testSafeAfterRecurringIncome() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_recur", "user_recur", reqDate, RequestType.purchase,
                new BigDecimal("1200.00"), reqDate.plusDays(60), true, "Appliance"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_recur", "USD", new BigDecimal("1000.00"), new BigDecimal("500.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        // Income 1 on day 10: 400. Balance becomes 1400. Paying 1200 leaves 200 < 500 min keep (not safe yet).
        LocalDate date1 = reqDate.plusDays(10);
        FinancialEvent inc1 = new FinancialEvent(
                "ev_inc1", "user_recur", "income", "Cycle 1", "salary",
                EventDirection.credit, new BigDecimal("400.00"), "USD",
                date1, date1, EventStatus.scheduled, null, EventFlexibility.fixed, null
        );
        // Income 2 on day 20: 400. Balance becomes 1800. Paying 1200 leaves 600 >= 500 min keep (safe on day 20!).
        LocalDate date2 = reqDate.plusDays(20);
        FinancialEvent inc2 = new FinancialEvent(
                "ev_inc2", "user_recur", "income", "Cycle 2", "salary",
                EventDirection.credit, new BigDecimal("400.00"), "USD",
                date2, date2, EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1000.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(inc1, inc2), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        Optional<LocalDate> earliest = earliestService.findEarliestDateForFullPayment(state);
        assertTrue(earliest.isPresent());
        assertEquals(date2, earliest.get(), "Must be safe upon second recurring income settlement");
    }

    @Test
    @DisplayName("4. Never Safe: Returns empty when amount exceeds full 90-day capacity")
    void testNeverSafe() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_never", "user_never", reqDate, RequestType.purchase,
                new BigDecimal("5000.00"), reqDate.plusDays(30), true, "Car"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_never", "USD", new BigDecimal("1000.00"), new BigDecimal("800.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1000.00"), new BigDecimal("800.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        Optional<LocalDate> earliest = earliestService.findEarliestDateForFullPayment(state);
        assertTrue(earliest.isEmpty(), "Expected no safe date within 90-day horizon for request exceeding capacity");
    }

    @Test
    @DisplayName("5. Minimum Balance Violation Before Payment Date: Rejects candidate dates if prior buffer breaches")
    void testPriorViolationRejectsCandidateDate() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_prior_viol", "user_prior", reqDate, RequestType.purchase,
                new BigDecimal("500.00"), reqDate.plusDays(30), true, "Camera"
        );
        // Balance = 1200, min keep = 1000.
        // On day 5, a large unavoidable expense of 400 hits, bringing balance to 800 (< 1000 min keep).
        // Then on day 15, salary of 3000 arrives, bringing balance to 3800.
        // Even though on day 20 balance is 3800, paying on day 20 is NOT safe because the 90-day
        // trajectory suffered a violation on day 5!
        FinancialProfile profile = new FinancialProfile(
                "user_prior", "USD", new BigDecimal("1200.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );

        FinancialEvent deficitExpense = new FinancialEvent(
                "ev_def", "user_prior", "expense", "Emergency fee", "utilities",
                EventDirection.debit, new BigDecimal("400.00"), "USD",
                reqDate.plusDays(4), reqDate.plusDays(4), EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialEvent lateSalary = new FinancialEvent(
                "ev_late_sal", "user_prior", "income", "Big Salary", "salary",
                EventDirection.credit, new BigDecimal("3000.00"), "USD",
                reqDate.plusDays(14), reqDate.plusDays(14), EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1200.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(lateSalary), List.of(deficitExpense),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        Optional<LocalDate> earliest = earliestService.findEarliestDateForFullPayment(state);
        // Since day 5 violates minimum balance, no payment schedule can be safe without spending changes!
        assertTrue(earliest.isEmpty(), "Baseline violation on day 5 must reject all candidate payment dates");
    }
}

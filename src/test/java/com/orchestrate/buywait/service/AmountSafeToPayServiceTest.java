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
class AmountSafeToPayServiceTest {

    @Autowired
    private AmountSafeToPayService safeToPayService;

    @Autowired
    private FinancialStateService financialStateService;

    @Test
    @DisplayName("1. Full Amount Safe: Requested amount is entirely affordable on request_date")
    void testFullAmountSafe() {
        // In dataset, request_01: requested_amount is 25256.00 ZAR and fully safe today
        FinancialState state = financialStateService.reconstructState("request_01");
        assertNotNull(state);

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        assertEquals(new BigDecimal("25256.00"), safe);

        // Binary search must find the exact same amount
        BigDecimal safeBinary = safeToPayService.calculateAmountSafeToPayWithBinarySearch(state);
        assertEquals(safe, safeBinary);
    }

    @Test
    @DisplayName("2. Partial Amount Safe: Only part of requested amount is safe before optional spending changes")
    void testPartialAmountSafe() {
        // In dataset, request_02: requested_amount is 46,018,000 IDR, safe is ~17,229,139.20 IDR
        FinancialState state = financialStateService.reconstructState("request_02");
        assertNotNull(state);

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        assertTrue(safe.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(safe.compareTo(state.request().requestedAmount()) < 0);

        BigDecimal safeBinary = safeToPayService.calculateAmountSafeToPayWithBinarySearch(state);
        assertEquals(safe, safeBinary);
    }

    @Test
    @DisplayName("3. Zero Safe Amount: When starting balance is already at or below minimum balance")
    void testZeroSafeAmount() {
        // Construct a state where starting balance equals minimum balance
        Request request = new Request(
                "req_test_zero", "user_zero", LocalDate.of(2024, 1, 1), RequestType.purchase,
                new BigDecimal("500.00"), LocalDate.of(2024, 1, 15), true, "Test item"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_zero", "USD", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        assertEquals(new BigDecimal("0.00"), safe);

        BigDecimal safeBinary = safeToPayService.calculateAmountSafeToPayWithBinarySearch(state);
        assertEquals(new BigDecimal("0.00"), safeBinary);
    }

    @Test
    @DisplayName("4. Future Salary: Upcoming confirmed salary creates safety buffer for payment")
    void testFutureSalarySupportsPayment() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_test_sal", "user_sal", reqDate, RequestType.purchase,
                new BigDecimal("400.00"), reqDate.plusDays(30), true, "Laptop"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_sal", "USD", new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        // Starting buffer is 1500 - 1000 = 500. Requested is 400.
        // Confirmed scheduled salary arriving on 2024-01-15
        FinancialEvent salary = new FinancialEvent(
                "ev_sal_1", "user_sal", "income", "Payroll", "salary",
                EventDirection.credit, new BigDecimal("2000.00"), "USD",
                reqDate.plusDays(14), reqDate.plusDays(14), EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), List.of(salary), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        // Requested amount of 400 is fully safe because buffer is 500 and salary replenishes funds
        assertEquals(new BigDecimal("400.00"), safe);
    }

    @Test
    @DisplayName("5. Future Rent: Large upcoming rent reduces the safe amount today")
    void testFutureRentReducesSafeAmount() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_test_rent", "user_rent", reqDate, RequestType.purchase,
                new BigDecimal("600.00"), reqDate.plusDays(30), true, "Appliance"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_rent", "USD", new BigDecimal("2000.00"), new BigDecimal("1000.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        // Starting available is 2000, min keep is 1000. Initial buffer looks like 1000.
        // BUT scheduled rent of 800 hits on 2024-01-05 before any salary!
        // So lowest balance without payment is 2000 - 800 = 1200.
        // Maximum payment safe on day 1 is 1200 - 1000 = 200!
        FinancialEvent rent = new FinancialEvent(
                "ev_rent_1", "user_rent", "expense", "Rent", "housing",
                EventDirection.debit, new BigDecimal("800.00"), "USD",
                reqDate.plusDays(4), reqDate.plusDays(4), EventStatus.scheduled, null, EventFlexibility.fixed, null
        );

        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("2000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList(), List.of(rent),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        assertEquals(new BigDecimal("200.00"), safe);

        BigDecimal safeBinary = safeToPayService.calculateAmountSafeToPayWithBinarySearch(state);
        assertEquals(new BigDecimal("200.00"), safeBinary);
    }

    @Test
    @DisplayName("6. Minimum Balance Constraints: Payment must strictly maintain minimum_balance_to_keep")
    void testStrictMinimumBalanceEnforcement() {
        LocalDate reqDate = LocalDate.of(2024, 1, 1);
        Request request = new Request(
                "req_test_min", "user_min", reqDate, RequestType.purchase,
                new BigDecimal("1000.00"), reqDate.plusDays(30), true, "Tools"
        );
        FinancialProfile profile = new FinancialProfile(
                "user_min", "USD", new BigDecimal("1500.00"), new BigDecimal("1200.00"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 12
        );
        // Available = 1500, min = 1200. Buffer = 300.
        FinancialState state = new FinancialState(
                profile, request, new BigDecimal("1500.00"), new BigDecimal("1200.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );

        BigDecimal safe = safeToPayService.calculateAmountSafeToPay(state);
        assertEquals(new BigDecimal("300.00"), safe);

        BigDecimal safeBinary = safeToPayService.calculateAmountSafeToPayWithBinarySearch(state);
        assertEquals(new BigDecimal("300.00"), safeBinary);
    }
}

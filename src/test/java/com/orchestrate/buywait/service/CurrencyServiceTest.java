package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CurrencyServiceTest {

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private FinancialDataRepository repository;

    @Test
    @DisplayName("1. Same Currency: Returns identical amount with proper 2-decimal scale")
    void testSameCurrency() {
        BigDecimal amount = new BigDecimal("1500.5");
        LocalDate date = LocalDate.of(2024, 1, 15);

        BigDecimal converted = currencyService.convert(amount, "INR", "INR", date);
        assertNotNull(converted);
        assertEquals(new BigDecimal("1500.50"), converted);
    }

    @Test
    @DisplayName("2. Foreign Currency: Direct rate lookup from dataset (EUR -> ZAR)")
    void testForeignCurrencyDirect() {
        // Dataset has: 2023-10-15,EUR,ZAR,20.00
        BigDecimal amount = new BigDecimal("100.00");
        LocalDate date = LocalDate.of(2023, 10, 15);

        BigDecimal converted = currencyService.convert(amount, "EUR", "ZAR", date);
        assertNotNull(converted);
        assertEquals(new BigDecimal("2000.00"), converted);
    }

    @Test
    @DisplayName("3. Reverse Pair: Converts using inverse rate when only direct pair is provided")
    void testReversePair() {
        // Dataset provides EUR -> ZAR rate of 20.00 on 2023-10-15
        // Converting 2000.00 ZAR to EUR must yield 100.00 EUR
        BigDecimal amount = new BigDecimal("2000.00");
        LocalDate date = LocalDate.of(2023, 10, 15);

        BigDecimal converted = currencyService.convert(amount, "ZAR", "EUR", date);
        assertNotNull(converted);
        assertEquals(new BigDecimal("100.00"), converted);
    }

    @Test
    @DisplayName("4. Multiple Dates: Correct rates applied for corresponding settlement dates")
    void testMultipleDates() {
        // USD -> EUR on 2023-10-15 is 0.92
        BigDecimal amount = new BigDecimal("1000.00");
        BigDecimal octRate = currencyService.convert(amount, "USD", "EUR", LocalDate.of(2023, 10, 15));
        assertEquals(new BigDecimal("920.00"), octRate);

        // EUR -> USD on 2024-04-15 is 1.09
        BigDecimal aprRate = currencyService.convert(amount, "EUR", "USD", LocalDate.of(2024, 4, 15));
        assertEquals(new BigDecimal("1090.00"), aprRate);
    }

    @Test
    @DisplayName("5. Rounding: Ensures HALF_UP rounding to 2 decimal places")
    void testRounding() {
        // 2024-01-15, USD -> INR is 83.33
        // 10.05 USD * 83.33 = 837.4665 -> rounds HALF_UP to 837.47
        BigDecimal amount = new BigDecimal("10.05");
        LocalDate date = LocalDate.of(2024, 1, 15);

        BigDecimal converted = currencyService.convert(amount, "USD", "INR", date);
        assertEquals(new BigDecimal("837.47"), converted);
    }

    @Test
    @DisplayName("6. Normalize Financial Event: Converts real foreign event from dataset to home currency")
    void testNormalizeFinancialEvent() {
        // event_2167 belongs to user_25 (home_currency IDR)
        // In financial_events.csv, event_2167 is USD 1800.00 on 2023-10-15
        // In exchange_rates.csv, 2023-10-15, USD -> IDR is 15833.33
        // 1800.00 * 15833.33 = 28,499,994.00 IDR
        var eventOpt = repository.findEventById("event_2167");
        assertTrue(eventOpt.isPresent(), "event_2167 should exist");

        FinancialEvent original = eventOpt.get();
        assertEquals("USD", original.currency());

        FinancialEvent normalized = currencyService.normalizeToHomeCurrency(original, "IDR");
        assertNotNull(normalized);
        assertEquals("IDR", normalized.currency());
        assertEquals(new BigDecimal("28499994.00"), normalized.amount());
    }

    @Test
    @DisplayName("7. Safe Failure: Throws CurrencyConversionException when pair cannot be resolved")
    void testMissingRateThrowsException() {
        assertThrows(CurrencyConversionException.class, () ->
                currencyService.convert(new BigDecimal("100"), "XYZ", "ABC", LocalDate.of(2024, 1, 15)));
    }
}

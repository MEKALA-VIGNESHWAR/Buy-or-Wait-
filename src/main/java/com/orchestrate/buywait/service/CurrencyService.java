package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CurrencyService {

    /**
     * Converts a monetary amount from source currency to target currency on a specific date.
     * Uses only dated exchange rates from dataset/exchange_rates.csv.
     *
     * @param amount       monetary amount in source currency (must not be null)
     * @param fromCurrency source currency code (INR, ZAR, IDR, USD, EUR)
     * @param toCurrency   target currency code
     * @param date         rate date / settlement date
     * @return converted amount scaled to 2 decimal places with HALF_UP rounding
     * @throws CurrencyConversionException if no rate or inverse rate exists
     */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date);

    /**
     * Retrieves the direct or derived exchange rate between two currencies on a given date.
     */
    Optional<BigDecimal> findEffectiveRate(String fromCurrency, String toCurrency, LocalDate date);

    /**
     * Normalizes a financial event to the user's home currency.
     * If the event is already in home currency or amount is null, returns the event unchanged.
     * Otherwise converts the amount using the event's settlement_date and updates the currency.
     */
    FinancialEvent normalizeToHomeCurrency(FinancialEvent event, String homeCurrency);

    /**
     * Normalizes a list of financial events to the user's home currency.
     */
    List<FinancialEvent> normalizeEventsToHomeCurrency(List<FinancialEvent> events, String homeCurrency);
}

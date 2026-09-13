package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Maps strictly to dataset/exchange_rates.csv.
 * Provides fixed conversion rates for foreign-currency cash events.
 */
public record ExchangeRate(
        LocalDate rateDate,
        String fromCurrency,
        String toCurrency,
        BigDecimal rate
) {
    /**
     * Converts an amount from source currency to target currency using this rate.
     */
    public BigDecimal convert(BigDecimal sourceAmount) {
        if (sourceAmount == null || rate == null) {
            return sourceAmount;
        }
        return sourceAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}

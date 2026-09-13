package com.orchestrate.buywait.service;

import java.time.LocalDate;

public class CurrencyConversionException extends RuntimeException {

    private final String fromCurrency;
    private final String toCurrency;
    private final LocalDate rateDate;

    public CurrencyConversionException(String fromCurrency, String toCurrency, LocalDate rateDate, String message) {
        super(String.format("Failed to convert from %s to %s for date %s: %s", fromCurrency, toCurrency, rateDate, message));
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.rateDate = rateDate;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.ExchangeRate;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CurrencyServiceImpl implements CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyServiceImpl.class);

    private static final int FINAL_SCALE = 2;
    private static final int INTERMEDIATE_DIVISION_SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final FinancialDataRepository repository;

    public CurrencyServiceImpl(FinancialDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (amount == null) {
            return null;
        }
        if (fromCurrency == null || toCurrency == null) {
            return amount.setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        String cleanFrom = fromCurrency.trim().toUpperCase();
        String cleanTo = toCurrency.trim().toUpperCase();

        // 1. Same Currency (Identity)
        if (cleanFrom.equals(cleanTo)) {
            return amount.setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        // 2. Direct Pair (from -> to) on date
        Optional<ExchangeRate> directRate = repository.findExchangeRate(date, cleanFrom, cleanTo);
        if (directRate.isPresent()) {
            BigDecimal rate = directRate.get().rate();
            return amount.multiply(rate).setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        // 3. Reverse Pair (to -> from) on date: amount / reverseRate
        Optional<ExchangeRate> reverseRate = repository.findExchangeRate(date, cleanTo, cleanFrom);
        if (reverseRate.isPresent()) {
            BigDecimal revRate = reverseRate.get().rate();
            if (revRate.compareTo(BigDecimal.ZERO) == 0) {
                throw new CurrencyConversionException(cleanFrom, cleanTo, date, "Exchange rate is zero");
            }
            return amount.divide(revRate, INTERMEDIATE_DIVISION_SCALE, ROUNDING_MODE)
                    .setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        // 4. Fallback: Search for closest available rate date for this currency pair in dataset
        Optional<ExchangeRate> nearestDirect = findNearestRate(cleanFrom, cleanTo, date);
        if (nearestDirect.isPresent()) {
            log.debug("Using nearest date rate {} for {}->{} on {}", nearestDirect.get().rateDate(), cleanFrom, cleanTo, date);
            return amount.multiply(nearestDirect.get().rate()).setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        Optional<ExchangeRate> nearestReverse = findNearestRate(cleanTo, cleanFrom, date);
        if (nearestReverse.isPresent()) {
            log.debug("Using nearest reverse rate {} for {}->{} on {}", nearestReverse.get().rateDate(), cleanTo, cleanFrom, date);
            return amount.divide(nearestReverse.get().rate(), INTERMEDIATE_DIVISION_SCALE, ROUNDING_MODE)
                    .setScale(FINAL_SCALE, ROUNDING_MODE);
        }

        throw new CurrencyConversionException(cleanFrom, cleanTo, date,
                "No exchange rate found for pair " + cleanFrom + " -> " + cleanTo);
    }

    @Override
    public Optional<BigDecimal> findEffectiveRate(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency == null || toCurrency == null) {
            return Optional.empty();
        }
        String cleanFrom = fromCurrency.trim().toUpperCase();
        String cleanTo = toCurrency.trim().toUpperCase();

        if (cleanFrom.equals(cleanTo)) {
            return Optional.of(BigDecimal.ONE.setScale(FINAL_SCALE, ROUNDING_MODE));
        }

        Optional<ExchangeRate> direct = repository.findExchangeRate(date, cleanFrom, cleanTo);
        if (direct.isPresent()) {
            return Optional.of(direct.get().rate());
        }

        Optional<ExchangeRate> reverse = repository.findExchangeRate(date, cleanTo, cleanFrom);
        if (reverse.isPresent()) {
            return Optional.of(BigDecimal.ONE.divide(reverse.get().rate(), INTERMEDIATE_DIVISION_SCALE, ROUNDING_MODE));
        }

        return Optional.empty();
    }

    @Override
    public FinancialEvent normalizeToHomeCurrency(FinancialEvent event, String homeCurrency) {
        if (event == null || homeCurrency == null || event.amount() == null) {
            return event;
        }
        String eventCurrency = event.currency();
        if (eventCurrency == null || eventCurrency.equalsIgnoreCase(homeCurrency)) {
            return event;
        }

        LocalDate rateDate = event.settlementDate() != null ? event.settlementDate() : event.eventDate();
        BigDecimal convertedAmount = convert(event.amount(), eventCurrency, homeCurrency, rateDate);
        return event.withAmountAndCurrency(convertedAmount, homeCurrency.toUpperCase());
    }

    @Override
    public List<FinancialEvent> normalizeEventsToHomeCurrency(List<FinancialEvent> events, String homeCurrency) {
        if (events == null || events.isEmpty() || homeCurrency == null) {
            return Collections.emptyList();
        }
        List<FinancialEvent> normalized = new ArrayList<>(events.size());
        for (FinancialEvent event : events) {
            normalized.add(normalizeToHomeCurrency(event, homeCurrency));
        }
        return Collections.unmodifiableList(normalized);
    }

    private Optional<ExchangeRate> findNearestRate(String fromCurrency, String toCurrency, LocalDate targetDate) {
        if (targetDate == null) {
            return Optional.empty();
        }
        List<ExchangeRate> allRates = repository.getAllExchangeRates();
        ExchangeRate closest = null;
        long minDiff = Long.MAX_VALUE;

        for (ExchangeRate rate : allRates) {
            if (fromCurrency.equalsIgnoreCase(rate.fromCurrency()) && toCurrency.equalsIgnoreCase(rate.toCurrency())) {
                long diff = Math.abs(ChronoUnit.DAYS.between(rate.rateDate(), targetDate));
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = rate;
                }
            }
        }
        return Optional.ofNullable(closest);
    }
}

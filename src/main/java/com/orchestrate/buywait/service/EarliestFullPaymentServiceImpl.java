package com.orchestrate.buywait.service;

import com.orchestrate.buywait.engine.ForecastResult;
import com.orchestrate.buywait.engine.ForecastSimulationEngine;
import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic service to calculate the earliest date on which paying the full requested amount
 * passes the 90-day safety check without optional spending changes.
 * <p>
 * Evaluates candidates chronologically from request_date to request_date + 90 days.
 * Independent of user payment-method preferences.
 */
@Service
public class EarliestFullPaymentServiceImpl implements EarliestFullPaymentService {

    private static final Logger log = LoggerFactory.getLogger(EarliestFullPaymentServiceImpl.class);

    private final ForecastSimulationEngine forecastEngine;

    public EarliestFullPaymentServiceImpl(ForecastSimulationEngine forecastEngine) {
        this.forecastEngine = forecastEngine;
    }

    @Override
    public Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState) {
        if (financialState == null || financialState.request() == null) {
            return Optional.empty();
        }
        return findEarliestDateForFullPayment(financialState, financialState.request().requestedAmount());
    }

    @Override
    public Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState, BigDecimal amount) {
        if (financialState == null || financialState.request() == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        LocalDate requestDate = financialState.request().requestDate();
        LocalDate horizonEnd = requestDate.plusDays(ForecastSimulationEngine.FORECAST_HORIZON_DAYS);

        // Iterate chronologically from request_date through 90-day forecast horizon
        for (LocalDate date = requestDate; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            List<Payment> candidatePlan = List.of(new Payment(date, amount));
            ForecastResult result = forecastEngine.simulate(financialState, candidatePlan);

            // Safety check:
            // 1. Minimum balance must never be violated across the entire 90-day horizon (before, during, and after payment)
            // 2. The payment itself must be affordable on that date
            if (!result.minimumBalanceViolated() && result.allPaymentsAffordable()) {
                log.debug("Found earliest date for full payment of {} for user {}: {}",
                        amount, financialState.profile().userId(), date);
                return Optional.of(date);
            }
        }

        log.debug("No safe date for full payment of {} found within 90-day horizon for user {}",
                amount, financialState.profile().userId());
        return Optional.empty();
    }
}

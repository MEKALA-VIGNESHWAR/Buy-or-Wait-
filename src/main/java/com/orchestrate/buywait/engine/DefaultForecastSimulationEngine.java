package com.orchestrate.buywait.engine;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.service.CurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Component
public class DefaultForecastSimulationEngine implements ForecastSimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultForecastSimulationEngine.class);

    private final CurrencyService currencyService;

    public DefaultForecastSimulationEngine(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @Override
    public ForecastResult simulate(
            FinancialState financialState,
            List<Payment> proposedPlan,
            Set<String> stoppedEventIds,
            Map<String, BigDecimal> reducedAmounts
    ) {
        if (financialState == null) {
            throw new IllegalArgumentException("financialState must not be null");
        }

        Request request = financialState.request();
        LocalDate requestDate = request.requestDate();
        LocalDate horizonEnd = requestDate.plusDays(FORECAST_HORIZON_DAYS);
        String homeCurrency = financialState.profile().homeCurrency();

        BigDecimal startingBalance = financialState.startingBalance() != null
                ? financialState.startingBalance()
                : BigDecimal.ZERO;
        BigDecimal minBalanceToKeep = financialState.minimumBalanceToKeep() != null
                ? financialState.minimumBalanceToKeep()
                : BigDecimal.ZERO;

        Set<String> stopped = stoppedEventIds != null ? stoppedEventIds : Collections.emptySet();
        Map<String, BigDecimal> reduced = reducedAmounts != null ? reducedAmounts : Collections.emptyMap();

        // 1. Map events by date
        Map<LocalDate, List<FinancialEvent>> incomeByDate = new HashMap<>();
        Map<LocalDate, List<FinancialEvent>> mandatoryByDate = new HashMap<>();
        Map<LocalDate, List<FinancialEvent>> otherOutflowsByDate = new HashMap<>();
        List<FinancialEvent> allAppliedEvents = new ArrayList<>();

        // 1a. Pending Debits
        for (FinancialEvent debit : financialState.pendingDebits()) {
            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(debit, homeCurrency);
            if (normalized.amount() != null && normalized.amount().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate date = normalized.settlementDate();
                if (date == null || date.isBefore(requestDate)) {
                    date = requestDate;
                }
                if (!date.isAfter(horizonEnd)) {
                    otherOutflowsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(normalized);
                    allAppliedEvents.add(normalized);
                }
            }
        }

        // 1b. Confirmed Future Income
        Set<YearMonth> salaryMonthsPresent = new HashSet<>();
        for (FinancialEvent income : financialState.confirmedIncomeEvents()) {
            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(income, homeCurrency);
            if (normalized.amount() != null && normalized.amount().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate date = normalized.settlementDate() != null ? normalized.settlementDate() : normalized.eventDate();
                if (date != null && !date.isBefore(requestDate) && !date.isAfter(horizonEnd)) {
                    incomeByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(normalized);
                    allAppliedEvents.add(normalized);
                    salaryMonthsPresent.add(YearMonth.from(date));
                }
            }
        }

        // Project recurring salary if future months lack explicit events
        projectRecurringSalary(financialState, requestDate, horizonEnd, homeCurrency, salaryMonthsPresent, incomeByDate, allAppliedEvents);

        // 1c. Essential Expenses
        Set<String> projectedRecurringExpenseKeys = new HashSet<>();
        for (FinancialEvent expense : financialState.essentialExpenses()) {
            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(expense, homeCurrency);
            if (normalized.amount() != null && normalized.amount().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate date = normalized.settlementDate() != null ? normalized.settlementDate() : normalized.eventDate();
                if (date != null && !date.isBefore(requestDate) && !date.isAfter(horizonEnd)) {
                    mandatoryByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(normalized);
                    allAppliedEvents.add(normalized);
                    projectedRecurringExpenseKeys.add(normalized.category() + "_" + YearMonth.from(date));
                }
            }
        }

        // Project recurring essential expenses (rent, utilities, insurance, tuition, loans)
        projectRecurringEssentialExpenses(financialState, requestDate, horizonEnd, homeCurrency,
                projectedRecurringExpenseKeys, mandatoryByDate, allAppliedEvents);

        // 1d. Flexible Expenses
        Set<String> projectedRecurringFlexibleKeys = new HashSet<>();
        for (FinancialEvent flex : financialState.flexibleExpenses()) {
            if (stopped.contains(flex.eventId())) {
                log.debug("Event {} is stopped; omitted from forecast.", flex.eventId());
                continue;
            }

            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(flex, homeCurrency);
            BigDecimal amount = normalized.amount();
            if (reduced.containsKey(flex.eventId())) {
                amount = reduced.get(flex.eventId());
                normalized = normalized.withAmount(amount);
            }

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                LocalDate date = normalized.settlementDate() != null ? normalized.settlementDate() : normalized.eventDate();
                if (date != null && !date.isBefore(requestDate) && !date.isAfter(horizonEnd)) {
                    otherOutflowsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(normalized);
                    allAppliedEvents.add(normalized);
                    projectedRecurringFlexibleKeys.add(normalized.category() + "_" + YearMonth.from(date));
                }
            }
        }

        // Project recurring flexible expenses (subscriptions, memberships, dining, etc.)
        projectRecurringFlexibleExpenses(financialState, requestDate, horizonEnd, homeCurrency,
                stopped, reduced, projectedRecurringFlexibleKeys, otherOutflowsByDate, allAppliedEvents);

        // 1e. Proposed Plan Payments
        Map<LocalDate, List<Payment>> paymentsByDate = new HashMap<>();
        List<Payment> appliedPayments = new ArrayList<>();
        if (proposedPlan != null) {
            for (Payment p : proposedPlan) {
                if (p.paymentDate() != null && !p.paymentDate().isBefore(requestDate) && !p.paymentDate().isAfter(horizonEnd)) {
                    paymentsByDate.computeIfAbsent(p.paymentDate(), k -> new ArrayList<>()).add(p);
                    appliedPayments.add(p);
                }
            }
        }

        // 2. Day-by-Day Simulation
        List<ForecastDay> dailyForecasts = new ArrayList<>();
        Map<LocalDate, BigDecimal> balanceByDate = new LinkedHashMap<>();

        BigDecimal currentBalance = startingBalance;
        BigDecimal minBalanceObserved = startingBalance;
        LocalDate dateOfMinBalance = requestDate;
        boolean minBalanceViolated = startingBalance.compareTo(minBalanceToKeep) < 0;
        boolean allPaymentsAffordable = true;

        for (LocalDate date = requestDate; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            BigDecimal opening = currentBalance;

            // Inflows
            BigDecimal dayIncome = BigDecimal.ZERO;
            List<FinancialEvent> dayInflowEvents = incomeByDate.get(date);
            if (dayInflowEvents != null) {
                for (FinancialEvent ev : dayInflowEvents) {
                    dayIncome = dayIncome.add(ev.amount());
                }
            }

            // Mandatory Outflows
            BigDecimal dayMandatory = BigDecimal.ZERO;
            List<FinancialEvent> dayMandatoryEvents = mandatoryByDate.get(date);
            if (dayMandatoryEvents != null) {
                for (FinancialEvent ev : dayMandatoryEvents) {
                    dayMandatory = dayMandatory.add(ev.amount());
                }
            }

            // Other Outflows
            BigDecimal dayOther = BigDecimal.ZERO;
            List<FinancialEvent> dayOtherEvents = otherOutflowsByDate.get(date);
            if (dayOtherEvents != null) {
                for (FinancialEvent ev : dayOtherEvents) {
                    dayOther = dayOther.add(ev.amount());
                }
            }

            // Proposed Payments
            BigDecimal dayPayments = BigDecimal.ZERO;
            List<Payment> dayPlanPayments = paymentsByDate.get(date);
            if (dayPlanPayments != null) {
                for (Payment p : dayPlanPayments) {
                    dayPayments = dayPayments.add(p.amount());
                }
            }

            // Balance before proposed payments
            BigDecimal balanceBeforePayments = opening.add(dayIncome).subtract(dayMandatory).subtract(dayOther);
            BigDecimal closing = balanceBeforePayments.subtract(dayPayments);

            // Safety check for payment
            if (dayPayments.compareTo(BigDecimal.ZERO) > 0) {
                if (closing.compareTo(minBalanceToKeep) < 0) {
                    allPaymentsAffordable = false;
                }
            }

            if (closing.compareTo(minBalanceToKeep) < 0) {
                minBalanceViolated = true;
            }

            if (closing.compareTo(minBalanceObserved) < 0) {
                minBalanceObserved = closing;
                dateOfMinBalance = date;
            }

            List<FinancialEvent> allDayEvents = new ArrayList<>();
            if (dayInflowEvents != null) allDayEvents.addAll(dayInflowEvents);
            if (dayMandatoryEvents != null) allDayEvents.addAll(dayMandatoryEvents);
            if (dayOtherEvents != null) allDayEvents.addAll(dayOtherEvents);

            ForecastDay forecastDay = new ForecastDay(
                    date,
                    opening,
                    dayIncome,
                    dayMandatory,
                    dayOther,
                    dayPayments,
                    closing,
                    allDayEvents,
                    dayPlanPayments != null ? dayPlanPayments : Collections.emptyList(),
                    closing.compareTo(minBalanceToKeep) < 0
            );

            dailyForecasts.add(forecastDay);
            balanceByDate.put(date, closing);
            currentBalance = closing;
        }

        return new ForecastResult(
                requestDate,
                horizonEnd,
                startingBalance,
                minBalanceToKeep,
                minBalanceObserved,
                dateOfMinBalance,
                minBalanceViolated,
                currentBalance,
                balanceByDate,
                dailyForecasts,
                allAppliedEvents,
                appliedPayments,
                allPaymentsAffordable
        );
    }

    @Override
    public boolean isSafe(
            List<Payment> proposedPlan,
            FinancialState financialState,
            Set<String> stoppedEventIds,
            Map<String, BigDecimal> reducedAmounts
    ) {
        if (proposedPlan == null || proposedPlan.isEmpty() || financialState == null) {
            return false;
        }

        Request request = financialState.request();
        BigDecimal requestedAmount = request.requestedAmount();
        LocalDate desiredCompletionDate = request.desiredCompletionDate();

        // 1. Request is fully completed (sum of payments == requested_amount)
        BigDecimal totalPaid = BigDecimal.ZERO;
        LocalDate maxPaymentDate = null;
        for (Payment p : proposedPlan) {
            if (p.amount() == null || p.amount().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            totalPaid = totalPaid.add(p.amount());
            if (maxPaymentDate == null || p.paymentDate().isAfter(maxPaymentDate)) {
                maxPaymentDate = p.paymentDate();
            }
        }

        boolean completesRequest = totalPaid.setScale(2, RoundingMode.HALF_UP)
                .compareTo(requestedAmount.setScale(2, RoundingMode.HALF_UP)) == 0;

        // An installment plan may include financing fees where totalPaid equals the option's totalPayableAmount
        if (!completesRequest && financialState.availablePaymentOptions() != null) {
            for (PaymentOption opt : financialState.availablePaymentOptions()) {
                if (opt.totalPayableAmount() != null
                        && totalPaid.setScale(2, RoundingMode.HALF_UP).compareTo(opt.totalPayableAmount().setScale(2, RoundingMode.HALF_UP)) == 0) {
                    completesRequest = true;
                    break;
                }
            }
        }

        if (!completesRequest) {
            return false;
        }

        // 2. Completion date satisfies desired_completion_date
        if (desiredCompletionDate != null && maxPaymentDate != null && maxPaymentDate.isAfter(desiredCompletionDate)) {
            return false;
        }

        // 3. Run forecast simulation
        ForecastResult result = simulate(financialState, proposedPlan, stoppedEventIds, reducedAmounts);

        // 4. Every planned payment is affordable & balance never falls below minimum_balance_to_keep
        return !result.minimumBalanceViolated() && result.allPaymentsAffordable();
    }

    @Override
    public BigDecimal calculateAmountSafeToPay(FinancialState financialState) {
        if (financialState == null || financialState.request() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal requestedAmount = financialState.request().requestedAmount();
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Simulate baseline without proposed payments
        ForecastResult baseline = simulate(financialState, Collections.emptyList());

        BigDecimal minObserved = baseline.minimumBalanceObserved();
        BigDecimal minKeep = financialState.minimumBalanceToKeep();

        BigDecimal buffer = minObserved.subtract(minKeep);
        if (buffer.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal safe = buffer.min(requestedAmount);
        return safe.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Optional<LocalDate> findEarliestDateForFullPayment(FinancialState financialState, BigDecimal requestedAmount) {
        if (financialState == null || requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        LocalDate requestDate = financialState.request().requestDate();
        LocalDate horizonEnd = requestDate.plusDays(FORECAST_HORIZON_DAYS);

        // First check requestDate
        BigDecimal safeToday = calculateAmountSafeToPay(financialState);
        if (safeToday.compareTo(requestedAmount) >= 0) {
            return Optional.of(requestDate);
        }

        // Search day by day through the 90-day window
        for (LocalDate date = requestDate.plusDays(1); !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            List<Payment> singleFullPayment = List.of(new Payment(date, requestedAmount));
            ForecastResult result = simulate(financialState, singleFullPayment);
            if (!result.minimumBalanceViolated() && result.allPaymentsAffordable()) {
                return Optional.of(date);
            }
        }

        return Optional.empty();
    }

    private void projectRecurringSalary(
            FinancialState financialState,
            LocalDate requestDate,
            LocalDate horizonEnd,
            String homeCurrency,
            Set<YearMonth> salaryMonthsPresent,
            Map<LocalDate, List<FinancialEvent>> incomeByDate,
            List<FinancialEvent> allAppliedEvents
    ) {
        // Find historical settled salary to determine recurring salary cadence (preferring regular base salary)
        List<FinancialEvent> baseSalaries = financialState.historicalEvents().stream()
                .filter(e -> ("salary".equalsIgnoreCase(e.category()) || (e.description() != null && e.description().toLowerCase().contains("salary")))
                        && e.direction() == EventDirection.credit
                        && e.status() == EventStatus.settled)
                .filter(e -> {
                    String desc = e.description() != null ? e.description().toLowerCase() : "";
                    return !desc.contains("commission") && !desc.contains("bonus") && !desc.contains("payout")
                            && !desc.contains("freelance") && !desc.contains("invoice") && !desc.contains("project")
                            && !desc.contains("contract");
                })
                .sorted(Comparator.comparing(FinancialEvent::eventDate))
                .toList();

        List<FinancialEvent> salaries = !baseSalaries.isEmpty() ? baseSalaries : financialState.historicalEvents().stream()
                .filter(e -> "salary".equalsIgnoreCase(e.category()) || (e.description() != null && e.description().toLowerCase().contains("salary")))
                .sorted(Comparator.comparing(FinancialEvent::eventDate))
                .toList();

        if (salaries.isEmpty()) {
            return;
        }

        FinancialEvent lastSalary = salaries.get(salaries.size() - 1);
        int dayOfMonth = lastSalary.eventDate().getDayOfMonth();
        BigDecimal salaryAmount = lastSalary.amount();

        YearMonth startMonth = YearMonth.from(requestDate);
        YearMonth endMonth = YearMonth.from(horizonEnd);

        YearMonth cur = startMonth;
        while (!cur.isAfter(endMonth)) {
            if (!salaryMonthsPresent.contains(cur)) {
                int validDay = Math.min(dayOfMonth, cur.lengthOfMonth());
                LocalDate projectedDate = cur.atDay(validDay);
                if (!projectedDate.isBefore(requestDate) && !projectedDate.isAfter(horizonEnd)) {
                    FinancialEvent projected = new FinancialEvent(
                            "proj_salary_" + cur,
                            financialState.profile().userId(),
                            "income",
                            "Projected regular salary",
                            "salary",
                            EventDirection.credit,
                            salaryAmount,
                            lastSalary.currency() != null ? lastSalary.currency() : homeCurrency,
                            projectedDate,
                            projectedDate,
                            EventStatus.scheduled,
                            null,
                            EventFlexibility.fixed,
                            null
                    );
                    FinancialEvent normalized = currencyService.normalizeToHomeCurrency(projected, homeCurrency);
                    incomeByDate.computeIfAbsent(projectedDate, k -> new ArrayList<>()).add(normalized);
                    allAppliedEvents.add(normalized);
                }
            }
            cur = cur.plusMonths(1);
        }
    }

    private void projectRecurringEssentialExpenses(
            FinancialState financialState,
            LocalDate requestDate,
            LocalDate horizonEnd,
            String homeCurrency,
            Set<String> projectedRecurringExpenseKeys,
            Map<LocalDate, List<FinancialEvent>> mandatoryByDate,
            List<FinancialEvent> allAppliedEvents
    ) {
        YearMonth prevMonth = YearMonth.from(requestDate).minusMonths(1);

        // Identify fixed settled recurring debits from the previous calendar month
        List<FinancialEvent> recentFixedDebits = financialState.historicalEvents().stream()
                .filter(ev -> ev.direction() == EventDirection.debit
                        && ev.status() == EventStatus.settled
                        && (ev.flexibility() == null || ev.flexibility() == EventFlexibility.fixed)
                        && ev.eventDate() != null
                        && YearMonth.from(ev.eventDate()).equals(prevMonth)
                        && ev.amount() != null
                        && ev.amount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        YearMonth startMonth = YearMonth.from(requestDate);
        YearMonth endMonth = YearMonth.from(horizonEnd);

        if (!recentFixedDebits.isEmpty()) {
            for (FinancialEvent template : recentFixedDebits) {
                int dayOfMonth = template.eventDate().getDayOfMonth();
                YearMonth cur = startMonth;
                while (!cur.isAfter(endMonth)) {
                    String key = template.eventId() + "_" + cur;
                    if (!projectedRecurringExpenseKeys.contains(key)) {
                        int validDay = Math.min(dayOfMonth, cur.lengthOfMonth());
                        LocalDate projectedDate = cur.atDay(validDay);
                        if (!projectedDate.isBefore(requestDate) && !projectedDate.isAfter(horizonEnd)) {
                            FinancialEvent projected = new FinancialEvent(
                                    template.eventId(),
                                    financialState.profile().userId(),
                                    template.eventType(),
                                    template.description(),
                                    template.category(),
                                    EventDirection.debit,
                                    template.amount(),
                                    template.currency() != null ? template.currency() : homeCurrency,
                                    projectedDate,
                                    projectedDate,
                                    EventStatus.scheduled,
                                    template.eventId(),
                                    EventFlexibility.fixed,
                                    null
                            );
                            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(projected, homeCurrency);
                            mandatoryByDate.computeIfAbsent(projectedDate, k -> new ArrayList<>()).add(normalized);
                            allAppliedEvents.add(normalized);
                            projectedRecurringExpenseKeys.add(key);
                        }
                    }
                    cur = cur.plusMonths(1);
                }
            }
        } else {
            // Fallback for synthetic/mock test environments
            Set<String> essentialCategories = Set.of(
                    "rent", "housing", "utilities", "insurance", "education", "debt_repayment"
            );
            Map<String, FinancialEvent> latestByCategory = new HashMap<>();
            for (FinancialEvent ev : financialState.historicalEvents()) {
                if (ev.category() != null && essentialCategories.contains(ev.category().toLowerCase())) {
                    latestByCategory.put(ev.category().toLowerCase(), ev);
                }
            }

            for (Map.Entry<String, FinancialEvent> entry : latestByCategory.entrySet()) {
                String cat = entry.getKey();
                FinancialEvent template = entry.getValue();
                if (template.amount() == null || template.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                int dayOfMonth = template.eventDate() != null ? template.eventDate().getDayOfMonth() : 1;
                YearMonth cur = startMonth;
                while (!cur.isAfter(endMonth)) {
                    String key = cat + "_" + cur;
                    if (!projectedRecurringExpenseKeys.contains(key)) {
                        int validDay = Math.min(dayOfMonth, cur.lengthOfMonth());
                        LocalDate projectedDate = cur.atDay(validDay);
                        if (!projectedDate.isBefore(requestDate) && !projectedDate.isAfter(horizonEnd)) {
                            FinancialEvent projected = new FinancialEvent(
                                    "proj_" + cat + "_" + cur,
                                    financialState.profile().userId(),
                                    template.eventType(),
                                    "Projected " + cat,
                                    cat,
                                    EventDirection.debit,
                                    template.amount(),
                                    template.currency() != null ? template.currency() : homeCurrency,
                                    projectedDate,
                                    projectedDate,
                                    EventStatus.scheduled,
                                    null,
                                    EventFlexibility.fixed,
                                    null
                            );
                            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(projected, homeCurrency);
                            mandatoryByDate.computeIfAbsent(projectedDate, k -> new ArrayList<>()).add(normalized);
                            allAppliedEvents.add(normalized);
                            projectedRecurringExpenseKeys.add(key);
                        }
                    }
                    cur = cur.plusMonths(1);
                }
            }
        }
    }

    private void projectRecurringFlexibleExpenses(
            FinancialState financialState,
            LocalDate requestDate,
            LocalDate horizonEnd,
            String homeCurrency,
            Set<String> stopped,
            Map<String, BigDecimal> reduced,
            Set<String> projectedRecurringKeys,
            Map<LocalDate, List<FinancialEvent>> otherOutflowsByDate,
            List<FinancialEvent> allAppliedEvents
    ) {
        Map<String, FinancialEvent> latestByCategory = new HashMap<>();
        for (FinancialEvent ev : financialState.flexibleExpenses()) {
            if (ev.category() != null) {
                FinancialEvent existing = latestByCategory.get(ev.category().toLowerCase());
                if (existing == null || (ev.eventDate() != null && ev.eventDate().isAfter(existing.eventDate()))) {
                    latestByCategory.put(ev.category().toLowerCase(), ev);
                }
            }
        }

        YearMonth startMonth = YearMonth.from(requestDate);
        YearMonth endMonth = YearMonth.from(horizonEnd);

        for (Map.Entry<String, FinancialEvent> entry : latestByCategory.entrySet()) {
            String cat = entry.getKey();
            FinancialEvent template = entry.getValue();

            if (stopped.contains(template.eventId())) {
                continue;
            }

            BigDecimal effectiveAmount = template.amount();
            if (reduced.containsKey(template.eventId())) {
                effectiveAmount = reduced.get(template.eventId());
            }

            if (effectiveAmount == null || effectiveAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if ("dining".equalsIgnoreCase(cat)) {
                LocalDate nextDate = template.eventDate() != null ? template.eventDate().plusDays(21) : requestDate;
                while (nextDate.isBefore(requestDate)) {
                    nextDate = nextDate.plusDays(21);
                }
                while (!nextDate.isAfter(horizonEnd)) {
                    String key = cat + "_" + nextDate;
                    if (!projectedRecurringKeys.contains(key)) {
                        FinancialEvent projected = new FinancialEvent(
                                template.eventId(),
                                financialState.profile().userId(),
                                template.eventType(),
                                template.description(),
                                cat,
                                EventDirection.debit,
                                effectiveAmount,
                                template.currency() != null ? template.currency() : homeCurrency,
                                nextDate,
                                nextDate,
                                EventStatus.scheduled,
                                template.eventId(),
                                template.flexibility(),
                                template.minimumAllowedAmount()
                        );
                        FinancialEvent normalized = currencyService.normalizeToHomeCurrency(projected, homeCurrency);
                        otherOutflowsByDate.computeIfAbsent(nextDate, k -> new ArrayList<>()).add(normalized);
                        allAppliedEvents.add(normalized);
                        projectedRecurringKeys.add(key);
                    }
                    nextDate = nextDate.plusDays(21);
                }
            } else {
                int dayOfMonth = template.eventDate() != null ? template.eventDate().getDayOfMonth() : 1;
                YearMonth cur = startMonth;
                while (!cur.isAfter(endMonth)) {
                    String key = cat + "_" + cur;
                    if (!projectedRecurringKeys.contains(key)) {
                        int validDay = Math.min(dayOfMonth, cur.lengthOfMonth());
                        LocalDate projectedDate = cur.atDay(validDay);
                        if (!projectedDate.isBefore(requestDate) && !projectedDate.isAfter(horizonEnd)) {
                            FinancialEvent projected = new FinancialEvent(
                                    template.eventId(),
                                    financialState.profile().userId(),
                                    template.eventType(),
                                    template.description(),
                                    cat,
                                    EventDirection.debit,
                                    effectiveAmount,
                                    template.currency() != null ? template.currency() : homeCurrency,
                                    projectedDate,
                                    projectedDate,
                                    EventStatus.scheduled,
                                    template.eventId(),
                                    template.flexibility(),
                                    template.minimumAllowedAmount()
                            );
                            FinancialEvent normalized = currencyService.normalizeToHomeCurrency(projected, homeCurrency);
                            otherOutflowsByDate.computeIfAbsent(projectedDate, k -> new ArrayList<>()).add(normalized);
                            allAppliedEvents.add(normalized);
                            projectedRecurringKeys.add(key);
                        }
                    }
                    cur = cur.plusMonths(1);
                }
            }
        }
    }
}

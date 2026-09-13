package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinancialStateServiceImpl implements FinancialStateService {

    private final RequestContextService requestContextService;
    private final FinancialEventNormalizer normalizer;
    private final FinancialDataRepository repository;

    public FinancialStateServiceImpl(
            RequestContextService requestContextService,
            FinancialEventNormalizer normalizer,
            FinancialDataRepository repository
    ) {
        this.requestContextService = requestContextService;
        this.normalizer = normalizer;
        this.repository = repository;
    }

    @Override
    public FinancialState reconstructState(String requestId) {
        RequestContext context = requestContextService.buildContext(requestId);
        return reconstructState(context);
    }

    @Override
    public FinancialState reconstructState(RequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        Request request = context.request();
        FinancialProfile profile = context.profile();
        LocalDate requestDate = request.requestDate();

        List<FinancialEvent> rawEvents = context.financialEvents();
        Map<String, FinancialEvent> eventsById = new HashMap<>();
        for (FinancialEvent event : rawEvents) {
            eventsById.put(event.eventId(), event);
        }

        // Apply conflict resolution
        List<FinancialEvent> resolvedEvents = normalizer.resolveConflicts(rawEvents, eventsById);

        List<FinancialEvent> pendingDebits = new ArrayList<>();
        List<FinancialEvent> confirmedIncomeEvents = new ArrayList<>();
        List<FinancialEvent> essentialExpenses = new ArrayList<>();
        List<FinancialEvent> flexibleExpenses = new ArrayList<>();
        List<FinancialEvent> historicalEvents = new ArrayList<>();
        List<FinancialEvent> ignoredEvents = new ArrayList<>();

        BigDecimal reservedPendingDebits = BigDecimal.ZERO;

        for (FinancialEvent event : resolvedEvents) {
            // Rule 1 & 2: Ignore failed and cancelled transactions
            if (normalizer.isFailedOrCancelled(event)) {
                ignoredEvents.add(event);
                continue;
            }

            // Rule 3: Ignore duplicate records
            if (normalizer.isDuplicate(event, eventsById)) {
                ignoredEvents.add(event);
                continue;
            }

            // Rule 5: Do not treat unrealized investment value as cash
            if (normalizer.isUnrealizedInvestment(event)) {
                ignoredEvents.add(event);
                continue;
            }

            // Rule 4: Ignore pending credits
            if (normalizer.isPendingCredit(event)) {
                ignoredEvents.add(event);
                continue;
            }

            // Active Pending Debits: Reserve them
            if (normalizer.isPendingDebit(event, eventsById)) {
                pendingDebits.add(event);
                if (event.amount() != null) {
                    reservedPendingDebits = reservedPendingDebits.add(event.amount());
                }
                continue;
            }

            // Rule 6: Count confirmed salary only on its settlement date
            if (normalizer.isConfirmedSalary(event, requestDate)) {
                confirmedIncomeEvents.add(event);
                continue;
            }

            // Flexible recurring expenses: active commitments that user permits adjusting
            if (normalizer.isRecurring(event) && normalizer.isFlexible(event, profile)) {
                flexibleExpenses.add(event);
            }

            // Historical transactions settled prior to request_date
            if (normalizer.isHistorical(event, requestDate)) {
                historicalEvents.add(event);
                continue;
            }

            // Future confirmed/scheduled expenses settling on or after request_date
            if (normalizer.isFutureExpense(event, requestDate)) {
                if (!normalizer.isFlexible(event, profile)) {
                    essentialExpenses.add(event);
                }
            }
        }

        BigDecimal startingBalance = profile.currentAvailableBalance() != null
                ? profile.currentAvailableBalance()
                : BigDecimal.ZERO;

        BigDecimal minimumBalanceToKeep = profile.minimumBalanceToKeep() != null
                ? profile.minimumBalanceToKeep()
                : BigDecimal.ZERO;

        return new FinancialState(
                profile,
                request,
                startingBalance,
                minimumBalanceToKeep,
                reservedPendingDebits,
                pendingDebits,
                confirmedIncomeEvents,
                essentialExpenses,
                flexibleExpenses,
                historicalEvents,
                ignoredEvents,
                context.paymentOptions(),
                context.messages(),
                context.images()
        );
    }
}

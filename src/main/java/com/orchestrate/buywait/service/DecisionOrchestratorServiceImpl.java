package com.orchestrate.buywait.service;

import com.orchestrate.buywait.ai.AiExplanationAgent;
import com.orchestrate.buywait.ai.EventUpdate;
import com.orchestrate.buywait.ai.EventUpdateAction;
import com.orchestrate.buywait.ai.EvidenceExtractionResult;
import com.orchestrate.buywait.ai.LlmEvidenceExtractor;
import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class DecisionOrchestratorServiceImpl implements DecisionOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(DecisionOrchestratorServiceImpl.class);

    private final FinancialDataRepository repository;
    private final RequestContextService requestContextService;
    private final LlmEvidenceExtractor llmEvidenceExtractor;
    private final ImageAnalysisService imageAnalysisService;
    private final FinancialStateService financialStateService;
    private final AmountSafeToPayService amountSafeToPayService;
    private final EarliestFullPaymentService earliestFullPaymentService;
    private final PaymentPlanService paymentPlanService;
    private final PlanVerifier planVerifier;
    private final PlanRanker planRanker;
    private final AiExplanationAgent aiExplanationAgent;

    public DecisionOrchestratorServiceImpl(
            FinancialDataRepository repository,
            RequestContextService requestContextService,
            LlmEvidenceExtractor llmEvidenceExtractor,
            ImageAnalysisService imageAnalysisService,
            FinancialStateService financialStateService,
            AmountSafeToPayService amountSafeToPayService,
            EarliestFullPaymentService earliestFullPaymentService,
            PaymentPlanService paymentPlanService,
            PlanVerifier planVerifier,
            PlanRanker planRanker,
            AiExplanationAgent aiExplanationAgent
    ) {
        this.repository = repository;
        this.requestContextService = requestContextService;
        this.llmEvidenceExtractor = llmEvidenceExtractor;
        this.imageAnalysisService = imageAnalysisService;
        this.financialStateService = financialStateService;
        this.amountSafeToPayService = amountSafeToPayService;
        this.earliestFullPaymentService = earliestFullPaymentService;
        this.paymentPlanService = paymentPlanService;
        this.planVerifier = planVerifier;
        this.planRanker = planRanker;
        this.aiExplanationAgent = aiExplanationAgent;
    }

    @Override
    public List<Prediction> evaluateAllRequests() {
        log.info("Starting complete decision orchestration for all requests...");
        List<Request> requests = repository.getAllRequests();
        List<Prediction> predictions = new ArrayList<>(requests.size());

        for (Request req : requests) {
            try {
                Prediction p = evaluateRequest(req.requestId());
                predictions.add(p);
            } catch (Exception e) {
                log.error("Error evaluating request {}: {}", req.requestId(), e.getMessage(), e);
                // Fail-safe deterministic fallback
                predictions.add(createFallbackPrediction(req));
            }
        }

        log.info("Completed evaluation for {} requests.", predictions.size());
        return predictions;
    }

    @Override
    public Prediction evaluateRequest(String requestId) {
        // Step 1: Load RequestContext
        RequestContext context = requestContextService.buildContext(requestId);
        if (context == null || context.request() == null) {
            throw new IllegalArgumentException("Request context not found: " + requestId);
        }
        Request request = context.request();

        // Step 2: Analyze relevant messages
        EvidenceExtractionResult msgEvidence = null;
        try {
            msgEvidence = llmEvidenceExtractor.extractEvidence(context);
        } catch (Exception e) {
            log.warn("Message evidence extraction failed for {}: {}", requestId, e.getMessage());
        }

        // Step 3: Analyze relevant images and resolve missing amounts
        List<FinancialEvent> eventsWithImages = imageAnalysisService.resolveMissingAmounts(
                context.financialEvents(), context.images());

        // Step 4: Apply message updates to financial events
        List<FinancialEvent> updatedEvents = applyMessageUpdates(eventsWithImages, msgEvidence);

        // Reconstruct normalized FinancialState
        RequestContext updatedContext = new RequestContext(
                context.request(),
                context.profile(),
                updatedEvents,
                context.paymentOptions(),
                context.messages(),
                context.images(),
                context.relevantExchangeRates()
        );
        FinancialState state = financialStateService.reconstructState(updatedContext);

        // Step 5: Calculate amount_safe_to_pay (before spending changes, between 0 and requested_amount)
        BigDecimal amountSafeToPay = amountSafeToPayService.calculateAmountSafeToPay(state);

        // Step 6: Calculate earliest_date_for_full_payment
        Optional<LocalDate> earliestDateOpt = earliestFullPaymentService.findEarliestDateForFullPayment(state);
        LocalDate earliestDate = earliestDateOpt.orElse(null);

        // Step 7 & 8: Generate candidate payment plans (including spending adjustment variants)
        List<Plan> candidatePlans = paymentPlanService.generateCandidatePlans(state);

        // Step 9 & 10: Independently verify every candidate plan using PlanVerifier
        List<Plan> verifiedPlans = new ArrayList<>();
        for (Plan plan : candidatePlans) {
            VerificationResult vr = planVerifier.verifyPlan(plan, amountSafeToPay, earliestDate, state);
            if (vr.isValid()) {
                verifiedPlans.add(plan);
            } else {
                log.debug("Candidate plan rejected for {}: method={}, violations={}",
                        requestId, plan.paymentMethod(), vr.violations());
            }
        }

        // Step 11 & 12: Rank and select best plan
        Optional<Plan> bestPlanOpt = planRanker.selectBestPlan(verifiedPlans, state);

        Plan selectedPlan = bestPlanOpt.orElseGet(Plan::notRecommended);
        AffordabilityStatus status;
        PaymentMethod method;
        List<Payment> paymentSchedule;
        LocalDate earliestDateForFull;
        List<SpendingChange> spendingChanges;

        if (selectedPlan.paymentMethod() == PaymentMethod.not_recommended) {
            status = AffordabilityStatus.not_affordable;
            method = PaymentMethod.not_recommended;
            paymentSchedule = Collections.emptyList();
            earliestDateForFull = earliestDate;
            spendingChanges = Collections.emptyList();
        } else if (selectedPlan.requiresSpendingChanges()) {
            status = AffordabilityStatus.affordable_with_plan;
            method = selectedPlan.paymentMethod();
            paymentSchedule = selectedPlan.payments();
            earliestDateForFull = earliestDate;
            spendingChanges = selectedPlan.spendingChanges();
        } else if (selectedPlan.paymentMethod() == PaymentMethod.full_payment) {
            status = AffordabilityStatus.affordable_now;
            method = PaymentMethod.full_payment;
            paymentSchedule = selectedPlan.payments();
            earliestDateForFull = request.requestDate();
            spendingChanges = Collections.emptyList();
        } else if (selectedPlan.paymentMethod() == PaymentMethod.wait) {
            status = AffordabilityStatus.affordable_later;
            method = PaymentMethod.wait;
            paymentSchedule = selectedPlan.payments();
            earliestDateForFull = earliestDate;
            spendingChanges = Collections.emptyList();
        } else {
            // installments or partial_payment without spending changes
            status = AffordabilityStatus.affordable_with_plan;
            method = selectedPlan.paymentMethod();
            paymentSchedule = selectedPlan.payments();
            earliestDateForFull = earliestDate;
            spendingChanges = Collections.emptyList();
        }

        Prediction interimPrediction = new Prediction(
                requestId,
                amountSafeToPay,
                status,
                method,
                paymentSchedule,
                earliestDateForFull,
                spendingChanges,
                ""
        );

        // Step 13 & 14: Generate fact-grounded explanation
        String explanation = aiExplanationAgent.generateExplanation(request, state, selectedPlan, interimPrediction);

        return new Prediction(
                requestId,
                amountSafeToPay,
                status,
                method,
                paymentSchedule,
                earliestDateForFull,
                spendingChanges,
                explanation
        );
    }

    private List<FinancialEvent> applyMessageUpdates(
            List<FinancialEvent> events,
            EvidenceExtractionResult msgEvidence
    ) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        if (msgEvidence == null || msgEvidence.eventUpdates() == null || msgEvidence.eventUpdates().isEmpty()) {
            return events;
        }

        Map<String, EventUpdate> updatesByEventId = new HashMap<>();
        for (EventUpdate u : msgEvidence.eventUpdates()) {
            if (u.eventId() != null && !u.eventId().isBlank()) {
                updatesByEventId.put(u.eventId(), u);
            }
        }

        List<FinancialEvent> updated = new ArrayList<>(events.size());
        for (FinancialEvent event : events) {
            EventUpdate update = updatesByEventId.get(event.eventId());
            if (update != null) {
                if (update.action() == EventUpdateAction.cancel) {
                    log.info("Applying message-based cancellation to event {}", event.eventId());
                    updated.add(new FinancialEvent(
                            event.eventId(), event.userId(), event.eventType(), event.description(),
                            event.category(), event.direction(), event.amount(), event.currency(),
                            event.eventDate(), event.settlementDate(), EventStatus.cancelled,
                            event.linkedEventId(), event.flexibility(), event.minimumAllowedAmount()
                    ));
                    continue;
                } else if (update.action() == EventUpdateAction.amend && update.newAmount() != null) {
                    log.info("Applying message-based amendment to event {}: new amount {}", event.eventId(), update.newAmount());
                    event = event.withAmount(update.newAmount());
                } else if (update.action() == EventUpdateAction.delay && update.newDate() != null) {
                    log.info("Applying message-based delay to event {}: new settlement date {}", event.eventId(), update.newDate());
                    event = new FinancialEvent(
                            event.eventId(), event.userId(), event.eventType(), event.description(),
                            event.category(), event.direction(), event.amount(), event.currency(),
                            event.eventDate(), update.newDate(), event.status(),
                            event.linkedEventId(), event.flexibility(), event.minimumAllowedAmount()
                    );
                } else if (update.action() == EventUpdateAction.confirm) {
                    log.info("Applying message-based confirmation to event {}", event.eventId());
                    event = new FinancialEvent(
                            event.eventId(), event.userId(), event.eventType(), event.description(),
                            event.category(), event.direction(), event.amount(), event.currency(),
                            event.eventDate(), event.settlementDate(), EventStatus.settled,
                            event.linkedEventId(), event.flexibility(), event.minimumAllowedAmount()
                    );
                }
            }
            updated.add(event);
        }

        return updated;
    }

    private Prediction createFallbackPrediction(Request req) {
        return new Prediction(
                req.requestId(),
                BigDecimal.ZERO,
                AffordabilityStatus.not_affordable,
                PaymentMethod.not_recommended,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                "Safe conservative fallback: payment not recommended based on current financial forecast."
        );
    }
}

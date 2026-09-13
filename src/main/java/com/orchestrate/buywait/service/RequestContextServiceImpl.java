package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RequestContextServiceImpl implements RequestContextService {

    private static final Logger log = LoggerFactory.getLogger(RequestContextServiceImpl.class);

    private final FinancialDataRepository repository;

    public RequestContextServiceImpl(FinancialDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public RequestContext buildContext(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be null or blank");
        }
        Request request = repository.findRequestById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found for id: " + requestId));
        return buildContext(request);
    }

    @Override
    public RequestContext buildContext(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        String userId = request.userId();
        String requestId = request.requestId();

        // 1. User profile (isolated to user_id)
        FinancialProfile profile = repository.findProfileByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Financial profile not found for user: " + userId));

        // 2. Financial events (isolated to user_id)
        List<FinancialEvent> userEvents = repository.findEventsByUserId(userId);
        Set<String> userEventIds = new HashSet<>();
        for (FinancialEvent event : userEvents) {
            userEventIds.add(event.eventId());
        }

        // 3. Payment options (isolated strictly to request_id)
        List<PaymentOption> requestOptions = repository.findPaymentOptionsByRequestId(requestId);

        // 4. Messages (isolated to user, request, or linked event)
        Map<String, Message> messageMap = new LinkedHashMap<>();
        for (Message m : repository.findMessagesByUserId(userId)) {
            messageMap.put(m.messageId(), m);
        }
        for (Message m : repository.findMessagesByRequestId(requestId)) {
            messageMap.put(m.messageId(), m);
        }
        for (String eventId : userEventIds) {
            for (Message m : repository.findMessagesByEventId(eventId)) {
                messageMap.put(m.messageId(), m);
            }
        }
        List<Message> relevantMessages = new ArrayList<>(messageMap.values());

        // 5. Images (isolated to user, request, or linked event)
        Map<String, ImageReference> imageMap = new LinkedHashMap<>();
        for (ImageReference img : repository.findImagesByUserId(userId)) {
            imageMap.put(img.imageId(), img);
        }
        for (ImageReference img : repository.findImagesByRequestId(requestId)) {
            imageMap.put(img.imageId(), img);
        }
        for (String eventId : userEventIds) {
            for (ImageReference img : repository.findImagesByEventId(eventId)) {
                imageMap.put(img.imageId(), img);
            }
        }
        List<ImageReference> relevantImages = new ArrayList<>(imageMap.values());

        // 6. Relevant exchange rates
        Set<String> userCurrencies = new HashSet<>();
        if (profile.homeCurrency() != null) {
            userCurrencies.add(profile.homeCurrency().toUpperCase());
        }
        for (FinancialEvent e : userEvents) {
            if (e.currency() != null) {
                userCurrencies.add(e.currency().toUpperCase());
            }
        }

        List<ExchangeRate> relevantRates = new ArrayList<>();
        for (ExchangeRate rate : repository.getAllExchangeRates()) {
            String from = rate.fromCurrency() != null ? rate.fromCurrency().toUpperCase() : "";
            String to = rate.toCurrency() != null ? rate.toCurrency().toUpperCase() : "";
            if (userCurrencies.contains(from) || userCurrencies.contains(to)) {
                relevantRates.add(rate);
            }
        }

        return new RequestContext(
                request,
                profile,
                userEvents,
                requestOptions,
                relevantMessages,
                relevantImages,
                relevantRates
        );
    }

    @Override
    public List<RequestContext> buildAllContexts() {
        List<Request> allRequests = repository.getAllRequests();
        List<RequestContext> contexts = new ArrayList<>(allRequests.size());
        for (Request request : allRequests) {
            contexts.add(buildContext(request));
        }
        log.info("Built {} complete request contexts.", contexts.size());
        return Collections.unmodifiableList(contexts);
    }
}

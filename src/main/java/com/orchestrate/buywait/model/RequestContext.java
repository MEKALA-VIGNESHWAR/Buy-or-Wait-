package com.orchestrate.buywait.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Complete, isolated context for evaluating a single financial request.
 * Contains the request, the user's profile, all user financial events,
 * request-specific payment options, relevant messages, linked images,
 * and applicable exchange rates.
 */
public record RequestContext(
        Request request,
        FinancialProfile profile,
        List<FinancialEvent> financialEvents,
        List<PaymentOption> paymentOptions,
        List<Message> messages,
        List<ImageReference> images,
        List<ExchangeRate> relevantExchangeRates
) {
    public RequestContext {
        financialEvents = financialEvents != null ? Collections.unmodifiableList(financialEvents) : Collections.emptyList();
        paymentOptions = paymentOptions != null ? Collections.unmodifiableList(paymentOptions) : Collections.emptyList();
        messages = messages != null ? Collections.unmodifiableList(messages) : Collections.emptyList();
        images = images != null ? Collections.unmodifiableList(images) : Collections.emptyList();
        relevantExchangeRates = relevantExchangeRates != null ? Collections.unmodifiableList(relevantExchangeRates) : Collections.emptyList();
    }

    public String requestId() {
        return request != null ? request.requestId() : null;
    }

    public String userId() {
        return request != null ? request.userId() : null;
    }

    public Optional<FinancialEvent> findEventById(String eventId) {
        if (eventId == null) {
            return Optional.empty();
        }
        return financialEvents.stream()
                .filter(e -> eventId.equals(e.eventId()))
                .findFirst();
    }

    public List<Message> findMessagesForEvent(String eventId) {
        if (eventId == null) {
            return Collections.emptyList();
        }
        return messages.stream()
                .filter(m -> eventId.equals(m.relatedEventId()))
                .toList();
    }

    public List<ImageReference> findImagesForEvent(String eventId) {
        if (eventId == null) {
            return Collections.emptyList();
        }
        return images.stream()
                .filter(img -> eventId.equals(img.relatedEventId()))
                .toList();
    }
}

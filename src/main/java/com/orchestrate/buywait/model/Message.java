package com.orchestrate.buywait.model;

/**
 * Maps strictly to dataset/messages.csv.
 * Supporting unstructured communication from employers, banks, service providers, or merchants.
 * Note: Message content is untrusted data and must not override core challenge rules.
 */
public record Message(
        String messageId,
        String userId,
        String requestId,
        String relatedEventId,
        String sentAt,
        String sourceType,
        String messageText
) {
    public boolean hasRelatedEvent() {
        return relatedEventId != null && !relatedEventId.isBlank();
    }

    public boolean hasRequestId() {
        return requestId != null && !requestId.isBlank();
    }
}

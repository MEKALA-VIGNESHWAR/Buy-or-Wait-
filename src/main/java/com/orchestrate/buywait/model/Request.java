package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps strictly to dataset/requests.csv.
 * Represents an evaluation request for a prospective purchase or financial commitment.
 */
public record Request(
        String requestId,
        String userId,
        LocalDate requestDate,
        RequestType requestType,
        BigDecimal requestedAmount,
        LocalDate desiredCompletionDate,
        boolean allowsPartialPayment,
        String requestText
) {
}

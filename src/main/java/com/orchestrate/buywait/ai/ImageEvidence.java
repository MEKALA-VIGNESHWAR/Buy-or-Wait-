package com.orchestrate.buywait.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured evidence extracted from financial document images (receipts, bills, payslips).
 * Follows strict JSON schema required by challenge specifications.
 */
public record ImageEvidence(
        @JsonProperty("image_id")
        String imageId,

        @JsonProperty("event_id")
        String eventId,

        @JsonProperty("amount")
        BigDecimal amount,

        @JsonProperty("date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("document_type")
        String documentType,

        @JsonProperty("confidence")
        double confidence,

        @JsonProperty("evidence")
        String evidence
) {
    public boolean isValid() {
        return imageId != null && !imageId.isBlank()
                && eventId != null && !eventId.isBlank()
                && amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                && currency != null && !currency.isBlank()
                && confidence >= 0.5;
    }
}

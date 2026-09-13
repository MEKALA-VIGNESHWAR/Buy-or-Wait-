package com.orchestrate.buywait.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a structured update to a financial event extracted from supporting evidence.
 */
public record EventUpdate(
        @JsonProperty("event_id")
        String eventId,

        @JsonProperty("action")
        EventUpdateAction action,

        @JsonProperty("new_amount")
        BigDecimal newAmount,

        @JsonProperty("new_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate newDate,

        @JsonProperty("confidence")
        double confidence,

        @JsonProperty("evidence")
        String evidence
) {
    public static EventUpdate cancel(String eventId, double confidence, String evidence) {
        return new EventUpdate(eventId, EventUpdateAction.cancel, null, null, confidence, evidence);
    }

    public static EventUpdate amend(String eventId, BigDecimal newAmount, double confidence, String evidence) {
        return new EventUpdate(eventId, EventUpdateAction.amend, newAmount, null, confidence, evidence);
    }

    public static EventUpdate delay(String eventId, LocalDate newDate, double confidence, String evidence) {
        return new EventUpdate(eventId, EventUpdateAction.delay, null, newDate, confidence, evidence);
    }

    public static EventUpdate confirm(String eventId, double confidence, String evidence) {
        return new EventUpdate(eventId, EventUpdateAction.confirm, null, null, confidence, evidence);
    }
}

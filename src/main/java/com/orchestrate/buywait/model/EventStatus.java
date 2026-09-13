package com.orchestrate.buywait.model;

/**
 * Lifecycle and cash state of financial events in financial_events.csv.
 */
public enum EventStatus {
    settled,
    pending,
    scheduled,
    cancelled,
    failed,
    unrealized;

    public static EventStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EventStatus.valueOf(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

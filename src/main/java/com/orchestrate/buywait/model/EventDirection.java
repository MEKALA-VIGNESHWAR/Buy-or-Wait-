package com.orchestrate.buywait.model;

/**
 * Cash direction of financial events in financial_events.csv.
 */
public enum EventDirection {
    debit,
    credit,
    non_cash;

    public static EventDirection fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EventDirection.valueOf(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

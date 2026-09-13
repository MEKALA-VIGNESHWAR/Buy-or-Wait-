package com.orchestrate.buywait.model;

/**
 * Flexibility of recurring expenses in financial_events.csv.
 */
public enum EventFlexibility {
    fixed,
    reducible,
    stoppable,
    reducible_or_stoppable;

    public static EventFlexibility fromString(String value) {
        if (value == null || value.isBlank()) {
            return fixed;
        }
        try {
            return EventFlexibility.valueOf(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return fixed;
        }
    }

    public boolean canStop() {
        return this == stoppable || this == reducible_or_stoppable;
    }

    public boolean canReduce() {
        return this == reducible || this == reducible_or_stoppable;
    }
}

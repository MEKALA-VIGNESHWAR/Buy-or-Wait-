package com.orchestrate.buywait.model;

/**
 * Fixed request categories specified in the problem statement.
 */
public enum RequestType {
    purchase,
    travel,
    education,
    family_transfer,
    debt_repayment,
    investment,
    housing,
    emergency_expense,
    other;

    public static RequestType fromString(String value) {
        if (value == null || value.isBlank()) {
            return other;
        }
        try {
            return RequestType.valueOf(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return other;
        }
    }
}

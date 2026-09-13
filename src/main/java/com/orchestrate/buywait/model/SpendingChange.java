package com.orchestrate.buywait.model;

import java.math.BigDecimal;

/**
 * Represents a permitted modification to a flexible recurring expense.
 * Supported formats:
 * - stop:<event_id>
 * - reduce_to:<event_id>:<new_amount>
 */
public record SpendingChange(
        SpendingChangeType type,
        String eventId,
        BigDecimal newAmount
) {
    public static SpendingChange stop(String eventId) {
        return new SpendingChange(SpendingChangeType.stop, eventId, null);
    }

    public static SpendingChange reduceTo(String eventId, BigDecimal newAmount) {
        return new SpendingChange(SpendingChangeType.reduce_to, eventId, newAmount);
    }

    public String toOutputFormat() {
        if (type == SpendingChangeType.stop) {
            return "stop:" + eventId;
        } else {
            if (newAmount == null) {
                return "reduce_to:" + eventId + ":0";
            }
            if (newAmount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                return "reduce_to:" + eventId + ":" + newAmount.toBigInteger().toString();
            } else {
                return "reduce_to:" + eventId + ":" + newAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            }
        }
    }
}

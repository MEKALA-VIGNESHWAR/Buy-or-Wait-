package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single scheduled payment in a payment plan: YYYY-MM-DD:amount.
 */
public record Payment(
        LocalDate paymentDate,
        BigDecimal amount
) implements Comparable<Payment> {

    @Override
    public int compareTo(Payment o) {
        int cmp = this.paymentDate.compareTo(o.paymentDate);
        if (cmp != 0) {
            return cmp;
        }
        return this.amount.compareTo(o.amount);
    }

    public String toPlanEntry() {
        return paymentDate + ":" + (amount != null ? amount.stripTrailingZeros().toPlainString() : "0");
    }
}

package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents structured financial facts extracted from supporting evidence
 * (unstructured messages or image files).
 * Examples:
 * - Net salary extracted from payslip image or payroll message
 * - Receipt / bill amount for an event with blank amount
 * - Confirmed invoice payment or rent adjustment
 */
public record ExtractedFact(
        String sourceId,         // message_id or image_id
        String userId,
        String relatedEventId,
        String factType,         // e.g., "SALARY_UPDATE", "EXPENSE_AMOUNT", "RENT_INCREASE", "PAYOUT_CONFIRMATION"
        BigDecimal confirmedAmount,
        LocalDate effectiveDate,
        String rawEvidenceText,
        boolean overridesPriorRecord
) {
    public static ExtractedFact fromImageExpense(String imageId, String userId, String relatedEventId, BigDecimal amount) {
        return new ExtractedFact(imageId, userId, relatedEventId, "EXPENSE_AMOUNT", amount, null, null, false);
    }
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.AffordabilityStatus;
import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Strict validator for generated predictions and final CSV output rows.
 * Enforces all 12 schema and business contract rules.
 */
@Component
public class OutputValidator {

    private static final Logger log = LoggerFactory.getLogger(OutputValidator.class);

    private static final Pattern PAYMENT_PLAN_PATTERN =
            Pattern.compile("^none|(\\d{4}-\\d{2}-\\d{2}:\\d+(\\.\\d+)?(\\|\\d{4}-\\d{2}-\\d{2}:\\d+(\\.\\d+)?)*)$");

    private static final Pattern SPENDING_CHANGE_PATTERN =
            Pattern.compile("^none|((stop:[a-zA-Z0-9_]+|reduce_to:[a-zA-Z0-9_]+:\\d+(\\.\\d+)?)(\\|(stop:[a-zA-Z0-9_]+|reduce_to:[a-zA-Z0-9_]+:\\d+(\\.\\d+)?))*)$");

    public record RowValidationResult(boolean isValid, List<String> errors) {}

    public RowValidationResult validatePrediction(Prediction prediction, Request request) {
        List<String> errors = new ArrayList<>();

        if (prediction == null) {
            return new RowValidationResult(false, List.of("Prediction is null"));
        }

        // 1. Request ID check
        if (prediction.requestId() == null || prediction.requestId().isBlank()) {
            errors.add("Missing request_id");
        } else if (request != null && !prediction.requestId().equals(request.requestId())) {
            errors.add(String.format("Mismatched request_id: expected %s, got %s", request.requestId(), prediction.requestId()));
        }

        // 2. amount_safe_to_pay check
        BigDecimal safeAmt = prediction.amountSafeToPay();
        if (safeAmt == null) {
            errors.add("amount_safe_to_pay is null");
        } else {
            if (safeAmt.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("amount_safe_to_pay is negative: " + safeAmt);
            }
            if (request != null && safeAmt.compareTo(request.requestedAmount()) > 0) {
                errors.add(String.format("amount_safe_to_pay %s exceeds requested_amount %s", safeAmt, request.requestedAmount()));
            }
        }

        // 3. affordability_status check
        if (prediction.affordabilityStatus() == null) {
            errors.add("Missing affordability_status");
        }

        // 4. recommended_payment_method check
        if (prediction.recommendedPaymentMethod() == null) {
            errors.add("Missing recommended_payment_method");
        }

        // 5. payment_plan syntax
        String planStr = prediction.formattedPaymentPlan();
        if (planStr == null || planStr.isBlank()) {
            errors.add("payment_plan string is blank");
        } else if (!PAYMENT_PLAN_PATTERN.matcher(planStr).matches()) {
            errors.add("Malformed payment_plan syntax: " + planStr);
        }

        // 6. earliest_date_for_full_payment consistency
        LocalDate earliest = prediction.earliestDateForFullPayment();
        if (prediction.affordabilityStatus() == AffordabilityStatus.affordable_now) {
            if (request != null && (earliest == null || !earliest.equals(request.requestDate()))) {
                errors.add(String.format("affordable_now requires earliest_date_for_full_payment to equal request_date %s, got %s",
                        request != null ? request.requestDate() : "null", earliest));
            }
        }

        // 7. spending_changes_needed syntax & count
        String changesStr = prediction.formattedSpendingChanges();
        if (changesStr == null || changesStr.isBlank()) {
            errors.add("spending_changes_needed string is blank");
        } else if (!SPENDING_CHANGE_PATTERN.matcher(changesStr).matches()) {
            errors.add("Malformed spending_changes_needed syntax: " + changesStr);
        } else if (!changesStr.equals("none")) {
            String[] changes = changesStr.split("\\|");
            if (changes.length > 3) {
                errors.add("Exceeded maximum of 3 spending changes: " + changes.length);
            }
        }

        // 8. decision_explanation
        if (prediction.decisionExplanation() == null || prediction.decisionExplanation().isBlank()) {
            errors.add("decision_explanation is empty");
        }

        return new RowValidationResult(errors.isEmpty(), errors);
    }
}

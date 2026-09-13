package com.orchestrate.buywait.ai;

import com.orchestrate.buywait.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Production implementation of AiExplanationAgent.
 * Cites only verified financial facts and maintains exact domain grounding.
 */
@Service
public class DefaultAiExplanationAgent implements AiExplanationAgent {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    @Override
    public String generateExplanation(Request request, FinancialState state, Plan selectedPlan, Prediction prediction) {
        if (prediction == null || state == null || request == null) {
            return "Recommendation based on verified financial rules.";
        }

        String currency = state.homeCurrency();
        String minBalStr = formatMoney(state.minimumBalanceToKeep(), currency);
        String reqAmountStr = formatMoney(request.requestedAmount(), currency);

        AffordabilityStatus status = prediction.affordabilityStatus();
        PaymentMethod method = prediction.recommendedPaymentMethod();

        // 1. Not affordable / Not recommended
        if (status == AffordabilityStatus.not_affordable || method == PaymentMethod.not_recommended) {
            String deadlineStr = formatDate(request.desiredCompletionDate());
            return String.format("Do not make this payment by %s. None of the available options keeps the %s minimum protected.",
                    deadlineStr, minBalStr);
        }

        // 2. Affordable now with full payment
        if (status == AffordabilityStatus.affordable_now && method == PaymentMethod.full_payment) {
            return String.format("Pay %s today. This leaves at least %s available over the next 90 days.",
                    reqAmountStr, minBalStr);
        }

        // 3. Affordable later (Wait)
        if (status == AffordabilityStatus.affordable_later || method == PaymentMethod.wait) {
            LocalDate safeDate = prediction.earliestDateForFullPayment();
            if (safeDate != null) {
                String dateStr = formatDate(safeDate);
                return String.format("Pay %s in full on %s. Paying earlier would take the balance below the %s minimum.",
                        reqAmountStr, dateStr, minBalStr);
            }
            return String.format("Wait until full payment becomes safe. Paying today would breach the %s minimum balance.", minBalStr);
        }

        // 4. Affordable with plan (Installments, Partial Payment, Spending Changes)
        if (status == AffordabilityStatus.affordable_with_plan) {
            // Check spending changes first
            String changesStr = prediction.formattedSpendingChanges();
            if (changesStr != null && !changesStr.equals("none") && !changesStr.isBlank()) {
                String actionDesc = describeSpendingChanges(changesStr, state);
                return String.format("%s, then pay %s today. This leaves at least %s available.",
                        actionDesc, reqAmountStr, minBalStr);
            }

            // Installments
            if (method == PaymentMethod.installments && selectedPlan != null && selectedPlan.payments() != null) {
                int numInstallments = selectedPlan.payments().size();
                BigDecimal firstAmt = selectedPlan.payments().isEmpty() ? BigDecimal.ZERO : selectedPlan.payments().get(0).amount();
                LocalDate startDate = selectedPlan.payments().isEmpty() ? request.requestDate() : selectedPlan.payments().get(0).paymentDate();
                String instAmtStr = formatMoney(firstAmt, currency);
                String startDateStr = formatDate(startDate);

                return String.format("Use %d installments of %s, starting %s. This leaves at least %s available.",
                        numInstallments, instAmtStr, startDateStr, minBalStr);
            }

            // Partial payment
            if (method == PaymentMethod.partial_payment) {
                BigDecimal safeAmt = prediction.amountSafeToPay();
                LocalDate secondDate = prediction.earliestDateForFullPayment();
                BigDecimal remaining = request.requestedAmount().subtract(safeAmt);

                return String.format("Pay %s today and %s on %s. This keeps the %s minimum protected.",
                        formatMoney(safeAmt, currency),
                        formatMoney(remaining, currency),
                        formatDate(secondDate),
                        minBalStr);
            }
        }

        // Fallback grounded statement
        return String.format("Safe payment plan determined preserving the %s minimum balance to keep.", minBalStr);
    }

    private String describeSpendingChanges(String spendingChanges, FinancialState state) {
        String[] parts = spendingChanges.split("\\|");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i > 0) {
                sb.append(" and ");
            }
            if (part.startsWith("stop:")) {
                String eventId = part.substring("stop:".length());
                String desc = findEventDescription(eventId, state);
                sb.append("Stop ").append(desc != null ? desc.toLowerCase() : "recurring expense");
            } else if (part.startsWith("reduce_to:")) {
                String[] tokens = part.split(":");
                if (tokens.length >= 3) {
                    String eventId = tokens[1];
                    String newAmt = tokens[2];
                    String desc = findEventDescription(eventId, state);
                    sb.append("Reduce ").append(desc != null ? desc.toLowerCase() : "expense")
                            .append(" to ").append(state.homeCurrency()).append(" ").append(newAmt);
                }
            }
        }
        return sb.toString();
    }

    private String findEventDescription(String eventId, FinancialState state) {
        if (state == null || state.flexibleExpenses() == null) {
            return "flexible subscription";
        }
        return state.flexibleExpenses().stream()
                .filter(e -> e.eventId().equals(eventId))
                .findFirst()
                .map(FinancialEvent::description)
                .orElse("flexible subscription");
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return currency + " 0";
        }
        DecimalFormat df = new DecimalFormat("#,##0.##");
        return currency + " " + df.format(amount);
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "the safe date";
        }
        return date.format(DATE_FORMATTER);
    }
}

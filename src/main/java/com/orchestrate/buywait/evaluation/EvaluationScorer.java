package com.orchestrate.buywait.evaluation;

import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.SampleRequest;
import com.orchestrate.buywait.service.OutputValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Deterministic scorer evaluating predictions against sample_requests.csv ground truth.
 * Strictly reads expected values dynamically from dataset; never hardcodes labels.
 */
@Component
public class EvaluationScorer {

    private final OutputValidator outputValidator;

    public EvaluationScorer(OutputValidator outputValidator) {
        this.outputValidator = outputValidator;
    }

    public EvaluationMetrics score(List<Prediction> predictions, List<SampleRequest> sampleRequests) {
        if (predictions == null || sampleRequests == null || sampleRequests.isEmpty()) {
            return new EvaluationMetrics(0, 0, 0, 0, 0, BigDecimal.ZERO, 0, 0, 0, Collections.emptyList());
        }

        Map<String, Prediction> predMap = new HashMap<>();
        for (Prediction p : predictions) {
            predMap.put(p.requestId(), p);
        }

        int total = sampleRequests.size();
        int correctStatus = 0;
        int correctMethod = 0;
        int correctEarliestDate = 0;
        int exactSafeAmt = 0;
        BigDecimal totalAbsError = BigDecimal.ZERO;
        int correctPlan = 0;
        int correctSpendingChanges = 0;
        int validSchemaCount = 0;

        List<EvaluationMetrics.DiscrepancyDiagnostic> diagnostics = new ArrayList<>();

        for (SampleRequest sample : sampleRequests) {
            String reqId = sample.request().requestId();
            Prediction expected = sample.expectedPrediction();
            Prediction actual = predMap.get(reqId);

            if (actual == null) {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(reqId, "ALL", "Prediction present", "Missing prediction"));
                continue;
            }

            // 1. Schema validity
            OutputValidator.RowValidationResult vr = outputValidator.validatePrediction(actual, sample.request());
            if (vr.isValid()) {
                validSchemaCount++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(reqId, "schema_validity", "valid", vr.errors().toString()));
            }

            // 2. Affordability Status
            if (Objects.equals(actual.affordabilityStatus(), expected.affordabilityStatus())) {
                correctStatus++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "affordability_status",
                        String.valueOf(expected.affordabilityStatus()),
                        String.valueOf(actual.affordabilityStatus())
                ));
            }

            // 3. Recommended Payment Method
            if (Objects.equals(actual.recommendedPaymentMethod(), expected.recommendedPaymentMethod())) {
                correctMethod++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "recommended_payment_method",
                        String.valueOf(expected.recommendedPaymentMethod()),
                        String.valueOf(actual.recommendedPaymentMethod())
                ));
            }

            // 4. Earliest Date for Full Payment
            if (Objects.equals(actual.earliestDateForFullPayment(), expected.earliestDateForFullPayment())) {
                correctEarliestDate++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "earliest_date_for_full_payment",
                        String.valueOf(expected.earliestDateForFullPayment()),
                        String.valueOf(actual.earliestDateForFullPayment())
                ));
            }

            // 5. Amount Safe to Pay
            BigDecimal expSafe = expected.amountSafeToPay() != null ? expected.amountSafeToPay() : BigDecimal.ZERO;
            BigDecimal actSafe = actual.amountSafeToPay() != null ? actual.amountSafeToPay() : BigDecimal.ZERO;
            BigDecimal absErr = expSafe.subtract(actSafe).abs();
            totalAbsError = totalAbsError.add(absErr);

            if (absErr.compareTo(new BigDecimal("0.01")) <= 0) {
                exactSafeAmt++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "amount_safe_to_pay",
                        expSafe.toPlainString(),
                        actSafe.toPlainString()
                ));
            }

            // 6. Payment Plan
            String expPlan = expected.formattedPaymentPlan();
            String actPlan = actual.formattedPaymentPlan();
            if (Objects.equals(expPlan, actPlan)) {
                correctPlan++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "payment_plan", expPlan, actPlan
                ));
            }

            // 7. Spending Changes
            String expChanges = expected.formattedSpendingChanges();
            String actChanges = actual.formattedSpendingChanges();
            if (Objects.equals(expChanges, actChanges)) {
                correctSpendingChanges++;
            } else {
                diagnostics.add(new EvaluationMetrics.DiscrepancyDiagnostic(
                        reqId, "spending_changes_needed", expChanges, actChanges
                ));
            }
        }

        BigDecimal mae = total > 0 ? totalAbsError.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new EvaluationMetrics(
                total,
                (double) correctStatus / total,
                (double) correctMethod / total,
                (double) correctEarliestDate / total,
                (double) exactSafeAmt / total,
                mae,
                (double) correctPlan / total,
                (double) correctSpendingChanges / total,
                (double) validSchemaCount / total,
                diagnostics
        );
    }
}

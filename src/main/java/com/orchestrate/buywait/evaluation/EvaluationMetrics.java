package com.orchestrate.buywait.evaluation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Record and data model for evaluation metrics against sample_requests.csv ground truth.
 */
public record EvaluationMetrics(
        int totalEvaluated,
        double affordabilityStatusAccuracy,
        double paymentMethodAccuracy,
        double earliestDateAccuracy,
        double amountSafeToPayExactMatchRate,
        BigDecimal amountSafeToPayMae,
        double paymentPlanExactMatchRate,
        double spendingChangesExactMatchRate,
        double schemaValidityRate,
        List<DiscrepancyDiagnostic> diagnostics
) {
    public record DiscrepancyDiagnostic(
            String requestId,
            String field,
            String expected,
            String predicted
    ) {}

    public String generateHumanReadableReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                      BUY OR WAIT EVALUATION REPORT                             \n");
        sb.append("================================================================================\n");
        sb.append(String.format("Total Solved Requests Evaluated:       %d\n", totalEvaluated));
        sb.append(String.format("Affordability Status Accuracy:         %.2f%%\n", affordabilityStatusAccuracy * 100));
        sb.append(String.format("Payment Method Accuracy:               %.2f%%\n", paymentMethodAccuracy * 100));
        sb.append(String.format("Earliest Safe Date Accuracy:           %.2f%%\n", earliestDateAccuracy * 100));
        sb.append(String.format("Amount Safe To Pay Exact Match Rate:   %.2f%%\n", amountSafeToPayExactMatchRate * 100));
        sb.append(String.format("Amount Safe To Pay MAE:                %s\n", amountSafeToPayMae.toPlainString()));
        sb.append(String.format("Payment Plan Exact Match Rate:         %.2f%%\n", paymentPlanExactMatchRate * 100));
        sb.append(String.format("Spending Changes Match Rate:           %.2f%%\n", spendingChangesExactMatchRate * 100));
        sb.append(String.format("Output Schema Validity Rate:           %.2f%%\n", schemaValidityRate * 100));
        sb.append("--------------------------------------------------------------------------------\n");

        if (diagnostics.isEmpty()) {
            sb.append("PERFECT ACCURACY: No discrepancies found across all evaluated sample requests!\n");
        } else {
            sb.append(String.format("DISCREPANCY DIAGNOSTICS (%d differences found):\n", diagnostics.size()));
            for (DiscrepancyDiagnostic d : diagnostics) {
                sb.append(String.format("  [%s] %s -> Expected: [%s] | Predicted: [%s]\n",
                        d.requestId(), d.field(), d.expected(), d.predicted()));
            }
        }
        sb.append("================================================================================\n");
        return sb.toString();
    }
}

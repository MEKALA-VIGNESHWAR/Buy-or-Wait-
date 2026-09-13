package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.AffordabilityStatus;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class OutputCsvServiceImpl implements OutputCsvService {

    private static final Logger log = LoggerFactory.getLogger(OutputCsvServiceImpl.class);

    private static final String CSV_HEADER =
            "request_id,amount_safe_to_pay,affordability_status,recommended_payment_method,payment_plan,earliest_date_for_full_payment,spending_changes_needed,decision_explanation";

    private final OutputValidator validator;

    public OutputCsvServiceImpl(OutputValidator validator) {
        this.validator = validator;
    }

    @Override
    public CsvGenerationSummary writeOutputCsv(List<Prediction> predictions, List<Request> requests, Path targetPath) {
        if (predictions == null || requests == null) {
            throw new IllegalArgumentException("predictions and requests must not be null");
        }

        if (predictions.size() != requests.size()) {
            throw new IllegalStateException(String.format(
                    "Prediction count (%d) does not match request count (%d)",
                    predictions.size(), requests.size()
            ));
        }

        // Map predictions by requestId to guarantee matching and order
        Map<String, Prediction> predictionMap = new HashMap<>();
        for (Prediction p : predictions) {
            predictionMap.put(p.requestId(), p);
        }

        int processed = requests.size();
        int validRows = 0;
        int invalidRows = 0;
        int safeNow = 0;
        int withPlan = 0;
        int later = 0;
        int notAffordable = 0;

        List<String> outputLines = new ArrayList<>(processed + 1);
        outputLines.add(CSV_HEADER);

        for (Request req : requests) {
            Prediction pred = predictionMap.get(req.requestId());
            if (pred == null) {
                log.error("Missing prediction for request_id: {}", req.requestId());
                invalidRows++;
                continue;
            }

            OutputValidator.RowValidationResult vr = validator.validatePrediction(pred, req);
            if (!vr.isValid()) {
                log.error("Validation failed for {}: {}", req.requestId(), vr.errors());
                invalidRows++;
            } else {
                validRows++;
            }

            // Categorize counts
            AffordabilityStatus st = pred.affordabilityStatus();
            if (st == AffordabilityStatus.affordable_now) {
                safeNow++;
            } else if (st == AffordabilityStatus.affordable_with_plan) {
                withPlan++;
            } else if (st == AffordabilityStatus.affordable_later) {
                later++;
            } else if (st == AffordabilityStatus.not_affordable) {
                notAffordable++;
            }

            String line = buildCsvLine(pred);
            outputLines.add(line);
        }

        CsvGenerationSummary summary = new CsvGenerationSummary(
                processed,
                validRows,
                invalidRows,
                safeNow,
                withPlan,
                later,
                notAffordable
        );

        summary.printSummary();

        if (summary.hasErrors()) {
            throw new IllegalStateException("CSV generation halted: " + invalidRows + " invalid rows detected.");
        }

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
                for (String line : outputLines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            log.info("Successfully wrote {} lines to {}", outputLines.size(), targetPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV file to " + targetPath, e);
        }

        return summary;
    }

    private String buildCsvLine(Prediction p) {
        return String.join(",",
                escapeCsv(p.requestId()),
                escapeCsv(p.formattedAmountSafeToPay()),
                escapeCsv(p.affordabilityStatus().name()),
                escapeCsv(p.recommendedPaymentMethod().name()),
                escapeCsv(p.formattedPaymentPlan()),
                escapeCsv(p.formattedEarliestDate()),
                escapeCsv(p.formattedSpendingChanges()),
                escapeCsv(p.decisionExplanation())
        );
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (needsQuotes) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

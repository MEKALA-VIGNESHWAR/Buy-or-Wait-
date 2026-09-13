package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;

import java.nio.file.Path;
import java.util.List;

public interface OutputCsvService {

    /**
     * Writes validated predictions to the target CSV file.
     * Enforces exact headers, ordering, and field formatting.
     *
     * @param predictions list of evaluated predictions
     * @param requests original list of requests to verify 1-to-1 matching and order
     * @param targetPath output path (e.g. output.csv in repo root)
     * @return summary of processed, valid, and categorical counts
     */
    CsvGenerationSummary writeOutputCsv(List<Prediction> predictions, List<Request> requests, Path targetPath);

    record CsvGenerationSummary(
            int requestsProcessed,
            int validRows,
            int invalidRows,
            int safeNowCount,
            int withPlanCount,
            int laterCount,
            int notAffordableCount
    ) {
        public boolean hasErrors() {
            return invalidRows > 0;
        }

        public void printSummary() {
            System.out.println("==================================================");
            System.out.println("               CSV OUTPUT SUMMARY                ");
            System.out.println("==================================================");
            System.out.println("Requests processed:     " + requestsProcessed);
            System.out.println("Valid rows:             " + validRows);
            System.out.println("Invalid rows:           " + invalidRows);
            System.out.println("Safe-now count:         " + safeNowCount);
            System.out.println("With-plan count:        " + withPlanCount);
            System.out.println("Later count:            " + laterCount);
            System.out.println("Not-affordable count:   " + notAffordableCount);
            System.out.println("==================================================");
        }
    }
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.evaluation.EvaluationMetrics;
import com.orchestrate.buywait.evaluation.EvaluationRunner;
import com.orchestrate.buywait.evaluation.UsageReportGenerator;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class EndToEndOrchestrationTest {

    @Autowired
    private FinancialDataRepository repository;

    @Autowired
    private DecisionOrchestratorService orchestratorService;

    @Autowired
    private OutputCsvService outputCsvService;

    @Autowired
    private EvaluationRunner evaluationRunner;

    @Autowired
    private UsageReportGenerator usageReportGenerator;

    @Test
    void testEndToEndOrchestrationAndValidation() throws Exception {
        // 1. Evaluate sample requests
        EvaluationMetrics evalMetrics = evaluationRunner.runEvaluation();
        assertNotNull(evalMetrics);
        assertTrue(evalMetrics.totalEvaluated() > 0, "Should evaluate sample requests");
        assertTrue(evalMetrics.affordabilityStatusAccuracy() > 0.5, "Status accuracy should be substantial");

        // 2. Evaluate all 250 requests
        List<Request> allRequests = repository.getAllRequests();
        assertEquals(250, allRequests.size(), "dataset/requests.csv must contain exactly 250 requests");

        List<Prediction> predictions = orchestratorService.evaluateAllRequests();
        assertEquals(250, predictions.size(), "Must generate exactly 250 predictions");

        // 3. Write output.csv to repository root
        Path outputPath = Paths.get("output.csv");
        OutputCsvService.CsvGenerationSummary summary =
                outputCsvService.writeOutputCsv(predictions, allRequests, outputPath);

        assertEquals(250, summary.requestsProcessed());
        assertEquals(250, summary.validRows(), "All 250 rows must pass strict OutputValidator");
        assertEquals(0, summary.invalidRows(), "Zero invalid rows allowed");

        assertTrue(Files.exists(outputPath), "output.csv must exist at repository root");
        List<String> lines = Files.readAllLines(outputPath);
        assertEquals(251, lines.size(), "output.csv must contain 1 header + 250 data rows");

        // 4. Generate usage_report.md
        Path reportPath = Paths.get("evaluation", "usage_report.md");
        usageReportGenerator.generateReport(predictions.size(), reportPath);
        assertTrue(Files.exists(reportPath), "evaluation/usage_report.md must exist");
    }
}

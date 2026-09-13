package com.orchestrate.buywait.runner;

import com.orchestrate.buywait.evaluation.EvaluationRunner;
import com.orchestrate.buywait.evaluation.UsageReportGenerator;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import com.orchestrate.buywait.repository.ValidationSummary;
import com.orchestrate.buywait.service.DecisionOrchestratorService;
import com.orchestrate.buywait.service.OutputCsvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class BuyOrWaitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BuyOrWaitRunner.class);

    private final FinancialDataRepository repository;
    private final DecisionOrchestratorService orchestratorService;
    private final OutputCsvService outputCsvService;
    private final EvaluationRunner evaluationRunner;
    private final UsageReportGenerator usageReportGenerator;

    @Value("${app.dataset.dir:dataset}")
    private String datasetDir;

    @Value("${app.output.file:output.csv}")
    private String outputFile;

    public BuyOrWaitRunner(
            FinancialDataRepository repository,
            DecisionOrchestratorService orchestratorService,
            OutputCsvService outputCsvService,
            EvaluationRunner evaluationRunner,
            UsageReportGenerator usageReportGenerator
    ) {
        this.repository = repository;
        this.orchestratorService = orchestratorService;
        this.outputCsvService = outputCsvService;
        this.evaluationRunner = evaluationRunner;
        this.usageReportGenerator = usageReportGenerator;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("================================================================================");
        log.info("  Buy or Wait? - HackerRank Orchestrate AI Financial Decision Agent");
        log.info("================================================================================");

        ValidationSummary summary = repository.getValidationSummary();
        if (summary != null) {
            log.info("\n{}", summary.toReportString());
        }

        // 1. Run Evaluation on Solved Sample Requests
        try {
            log.info("Evaluating predictions against solved sample requests...");
            evaluationRunner.runEvaluation();
        } catch (Exception e) {
            log.warn("Sample evaluation encountered an issue: {}", e.getMessage(), e);
        }

        // 2. Evaluate All Evaluation Requests from dataset/requests.csv
        List<Request> allRequests = repository.getAllRequests();
        log.info("Evaluating all {} evaluation requests...", allRequests.size());
        List<Prediction> predictions = orchestratorService.evaluateAllRequests();

        // 3. Validate and Export output.csv
        Path targetPath = Paths.get(outputFile);
        log.info("Exporting validated predictions to {}...", targetPath.toAbsolutePath());
        OutputCsvService.CsvGenerationSummary csvSummary =
                outputCsvService.writeOutputCsv(predictions, allRequests, targetPath);

        // 4. Generate evaluation/usage_report.md
        log.info("Generating AI token usage report...");
        usageReportGenerator.generateReport(predictions.size());

        log.info("================================================================================");
        log.info("Execution complete! output.csv generated successfully with {} valid rows.", csvSummary.validRows());
        log.info("================================================================================");
    }
}

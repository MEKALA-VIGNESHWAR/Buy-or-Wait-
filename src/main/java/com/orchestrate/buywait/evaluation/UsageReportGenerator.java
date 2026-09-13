package com.orchestrate.buywait.evaluation;

import com.orchestrate.buywait.ai.TokenUsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates the required evaluation/usage_report.md artifact summarizing
 * AI model calls, token consumption, and estimated costs.
 * Strictly redacts and excludes any API keys or secrets.
 */
@Component
public class UsageReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(UsageReportGenerator.class);

    private final TokenUsageTracker tracker;

    public UsageReportGenerator(TokenUsageTracker tracker) {
        this.tracker = tracker;
    }

    public void generateReport(int totalRequestsEvaluated) {
        Path defaultPath = Paths.get("evaluation", "usage_report.md");
        generateReport(totalRequestsEvaluated, defaultPath);
    }

    public void generateReport(int totalRequestsEvaluated, Path outputPath) {
        String markdown = tracker.generateUsageReport(totalRequestsEvaluated);

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                writer.write(markdown);
            }
            log.info("Saved AI token usage report to {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write usage report to {}: {}", outputPath, e.getMessage(), e);
        }
    }
}

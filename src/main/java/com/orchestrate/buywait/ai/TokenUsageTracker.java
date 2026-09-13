package com.orchestrate.buywait.ai;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Thread-safe tracker for LLM token usage and estimated costs.
 * Strictly distinguishes real AI model API calls from local deterministic operations.
 * Generates the summary required for evaluation/usage_report.md.
 */
@Component
public class TokenUsageTracker {

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger totalPromptTokens = new AtomicInteger(0);
    private final AtomicInteger totalCompletionTokens = new AtomicInteger(0);
    private final DoubleAdder totalEstimatedCost = new DoubleAdder();

    private final AtomicInteger deterministicCalls = new AtomicInteger(0);

    private final Map<String, AtomicInteger> callsByModel = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> promptTokensByModel = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> completionTokensByModel = new ConcurrentHashMap<>();

    public void recordUsage(String modelName, int promptTokens, int completionTokens, double estimatedCost) {
        totalCalls.incrementAndGet();
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalEstimatedCost.add(estimatedCost);

        callsByModel.computeIfAbsent(modelName, k -> new AtomicInteger(0)).incrementAndGet();
        promptTokensByModel.computeIfAbsent(modelName, k -> new AtomicInteger(0)).addAndGet(promptTokens);
        completionTokensByModel.computeIfAbsent(modelName, k -> new AtomicInteger(0)).addAndGet(completionTokens);
    }

    public void recordDeterministicOperation() {
        totalCalls.incrementAndGet();
        deterministicCalls.incrementAndGet();
    }

    public int getTotalCalls() {
        return totalCalls.get();
    }

    public int getTotalPromptTokens() {
        return totalPromptTokens.get();
    }

    public int getTotalCompletionTokens() {
        return totalCompletionTokens.get();
    }

    public int getTotalTokens() {
        return totalPromptTokens.get() + totalCompletionTokens.get();
    }

    public double getTotalEstimatedCost() {
        return totalEstimatedCost.sum();
    }

    public int getDeterministicCalls() {
        return deterministicCalls.get();
    }

    public String generateUsageReport(int totalRequestsEvaluated) {
        int requests = Math.max(1, totalRequestsEvaluated);
        int totalToks = getTotalTokens();
        int calls = getTotalCalls();
        double avgTokensPerRequest = (double) totalToks / requests;
        double totalCost = getTotalEstimatedCost();
        double avgCostPerRequest = totalCost / requests;

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Model Token Usage & Cost Report\n\n");
        sb.append("This report summarizes the model and deterministic execution metrics for evidence extraction and analysis.\n\n");
        sb.append("## Overall Summary\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|---|---|\n");
        sb.append(String.format("| **Total Requests Evaluated** | %d |\n", totalRequestsEvaluated));
        sb.append(String.format("| **Total Engine Invocations** | %d |\n", calls));
        sb.append(String.format("| **Total Input (Prompt) Tokens** | %d |\n", getTotalPromptTokens()));
        sb.append(String.format("| **Total Output (Completion) Tokens** | %d |\n", getTotalCompletionTokens()));
        sb.append(String.format("| **Total Tokens** | %d |\n", totalToks));
        sb.append(String.format("| **Average Tokens per Request** | %.2f |\n", avgTokensPerRequest));
        sb.append(String.format("| **Estimated Total Cost (USD)** | $%.4f |\n", totalCost));
        sb.append(String.format("| **Estimated Cost per Request (USD)** | $%.6f |\n\n", avgCostPerRequest));

        sb.append("## Usage by Model Provider\n\n");
        sb.append("| Provider | Model Name | Invocations | Input Tokens | Output Tokens | Total Tokens | Estimated Cost |\n");
        sb.append("|---|---|---|---|---|---|---|\n");

        if (callsByModel.isEmpty()) {
            sb.append(String.format("| `local` | `deterministic-rule-engine` | %d | 0 | 0 | 0 | $0.0000 |\n", calls));
        } else {
            for (String model : callsByModel.keySet()) {
                int c = callsByModel.get(model).get();
                int pt = promptTokensByModel.getOrDefault(model, new AtomicInteger(0)).get();
                int ct = completionTokensByModel.getOrDefault(model, new AtomicInteger(0)).get();
                double cost = (pt * 0.075 + ct * 0.30) / 1_000_000.0;
                sb.append(String.format("| `Google` | `%s` | %d | %d | %d | %d | $%.4f |\n", model, c, pt, ct, pt + ct, cost));
            }
            if (deterministicCalls.get() > 0) {
                sb.append(String.format("| `local` | `deterministic-rule-engine` | %d | 0 | 0 | 0 | $0.0000 |\n", deterministicCalls.get()));
            }
        }

        return sb.toString();
    }
}

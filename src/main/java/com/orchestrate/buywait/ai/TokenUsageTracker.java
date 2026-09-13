package com.orchestrate.buywait.ai;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Thread-safe tracker for LLM token usage and estimated costs.
 * Generates the summary required for evaluation/usage_report.md.
 */
@Component
public class TokenUsageTracker {

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger totalPromptTokens = new AtomicInteger(0);
    private final AtomicInteger totalCompletionTokens = new AtomicInteger(0);
    private final DoubleAdder totalEstimatedCost = new DoubleAdder();

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

    public String generateUsageReport(int totalRequestsEvaluated) {
        int requests = Math.max(1, totalRequestsEvaluated);
        int totalToks = getTotalTokens();
        int calls = getTotalCalls();
        double avgTokensPerRequest = (double) totalToks / requests;
        double totalCost = getTotalEstimatedCost();
        double avgCostPerRequest = totalCost / requests;

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Model Token Usage & Cost Report\n\n");
        sb.append("This report summarizes the LLM usage for evidence extraction and multimodal analysis.\n\n");
        sb.append("## Overall Summary\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|---|---|\n");
        sb.append(String.format("| **Total Requests Evaluated** | %d |\n", totalRequestsEvaluated));
        sb.append(String.format("| **Total Model Invocations** | %d |\n", calls));
        sb.append(String.format("| **Total Input (Prompt) Tokens** | %d |\n", getTotalPromptTokens()));
        sb.append(String.format("| **Total Output (Completion) Tokens** | %d |\n", getTotalCompletionTokens()));
        sb.append(String.format("| **Total Tokens** | %d |\n", totalToks));
        sb.append(String.format("| **Average Tokens per Request** | %.2f |\n", avgTokensPerRequest));
        sb.append(String.format("| **Estimated Total Cost (USD)** | $%.4f |\n", totalCost));
        sb.append(String.format("| **Estimated Cost per Request (USD)** | $%.6f |\n\n", avgCostPerRequest));

        sb.append("## Usage by Model Provider\n\n");
        sb.append("| Model Name | Calls | Input Tokens | Output Tokens | Total Tokens |\n");
        sb.append("|---|---|---|---|---|\n");

        if (callsByModel.isEmpty()) {
            sb.append("| Deterministic Engine (Offline Fallback) | ").append(calls).append(" | 0 | 0 | 0 |\n");
        } else {
            for (String model : callsByModel.keySet()) {
                int c = callsByModel.get(model).get();
                int pt = promptTokensByModel.getOrDefault(model, new AtomicInteger(0)).get();
                int ct = completionTokensByModel.getOrDefault(model, new AtomicInteger(0)).get();
                sb.append(String.format("| `%s` | %d | %d | %d | %d |\n", model, c, pt, ct, pt + ct));
            }
        }

        return sb.toString();
    }
}

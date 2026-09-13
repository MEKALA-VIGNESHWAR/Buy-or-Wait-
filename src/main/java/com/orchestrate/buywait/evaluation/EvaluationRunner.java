package com.orchestrate.buywait.evaluation;

import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.SampleRequest;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import com.orchestrate.buywait.service.DecisionOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runner that executes evaluation of predictions against dataset/sample_requests.csv.
 */
@Component
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    private final FinancialDataRepository repository;
    private final DecisionOrchestratorService orchestratorService;
    private final EvaluationScorer scorer;

    public EvaluationRunner(
            FinancialDataRepository repository,
            DecisionOrchestratorService orchestratorService,
            EvaluationScorer scorer
    ) {
        this.repository = repository;
        this.orchestratorService = orchestratorService;
        this.scorer = scorer;
    }

    public EvaluationMetrics runEvaluation() {
        log.info("Starting evaluation against dataset/sample_requests.csv...");
        List<SampleRequest> sampleRequests = repository.getAllSampleRequests();
        if (sampleRequests == null || sampleRequests.isEmpty()) {
            log.warn("No sample requests found for evaluation.");
            return scorer.score(List.of(), List.of());
        }

        List<Prediction> predictions = new ArrayList<>(sampleRequests.size());
        for (SampleRequest sr : sampleRequests) {
            String reqId = sr.request().requestId();
            Prediction p = orchestratorService.evaluateRequest(reqId);
            predictions.add(p);
        }

        EvaluationMetrics metrics = scorer.score(predictions, sampleRequests);
        log.info("\n{}", metrics.generateHumanReadableReport());
        System.out.println(metrics.generateHumanReadableReport());
        return metrics;
    }
}

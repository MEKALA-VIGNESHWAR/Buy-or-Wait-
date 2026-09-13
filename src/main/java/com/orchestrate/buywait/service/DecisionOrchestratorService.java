package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.Prediction;

import java.util.List;

public interface DecisionOrchestratorService {

    /**
     * Evaluates all requests in dataset/requests.csv sequentially.
     */
    List<Prediction> evaluateAllRequests();

    /**
     * Evaluates a single request by requestId.
     */
    Prediction evaluateRequest(String requestId);
}

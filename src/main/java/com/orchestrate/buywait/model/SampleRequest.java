package com.orchestrate.buywait.model;

/**
 * Represents a solved sample request from dataset/sample_requests.csv.
 * Combines the input request with the ground-truth prediction.
 */
public record SampleRequest(
        Request request,
        Prediction expectedPrediction
) {
}

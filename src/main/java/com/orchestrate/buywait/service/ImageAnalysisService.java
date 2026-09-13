package com.orchestrate.buywait.service;

import com.orchestrate.buywait.ai.ImageEvidence;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.ImageReference;
import com.orchestrate.buywait.model.RequestContext;

import java.util.List;
import java.util.Map;

/**
 * Service to analyze financial documents/images linked in images.csv.
 * Resolves missing amounts for events without treating blank amounts as zero.
 */
public interface ImageAnalysisService {

    /**
     * Enriches financial events by extracting missing amounts from corresponding images.
     *
     * @param events          raw financial events
     * @param imageReferences images linked to user or request
     * @return enriched financial events with verified amounts
     */
    List<FinancialEvent> resolveMissingAmounts(List<FinancialEvent> events, List<ImageReference> imageReferences);

    /**
     * Extracts structured evidence for all images in the request context.
     *
     * @param context request context containing images
     * @return map of imageId to extracted ImageEvidence
     */
    Map<String, ImageEvidence> extractEvidence(RequestContext context);
}

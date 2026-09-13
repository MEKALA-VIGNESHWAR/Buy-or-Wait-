package com.orchestrate.buywait.ai;

import java.nio.file.Path;

/**
 * Interface for multimodal/VLM evidence extraction from financial images.
 * Designed to be provider-agnostic (Gemini, OpenAI Vision, or local OCR/VLM fallback).
 *
 * CRITICAL ARCHITECTURE RULE:
 * The VLM is an evidence extractor only; it must never make financial affordability decisions.
 */
public interface ImageEvidenceExtractor {

    /**
     * Extracts financial details (amount, date, currency, document type) from a document image.
     *
     * @param imageId   the unique image identifier (e.g. "image_01")
     * @param eventId   the financial event ID related to this image (e.g. "event_253")
     * @param imagePath absolute or relative path to the image file
     * @return structured evidence record
     */
    ImageEvidence extractEvidence(String imageId, String eventId, Path imagePath);

    /**
     * Resolves the image path automatically under dataset/media/images/<imageId>.png and extracts evidence.
     *
     * @param imageId the unique image identifier
     * @param eventId the related event id
     * @return structured evidence record
     */
    ImageEvidence extractEvidence(String imageId, String eventId);

    /**
     * Clears internal in-memory cache.
     */
    void clearCache();
}

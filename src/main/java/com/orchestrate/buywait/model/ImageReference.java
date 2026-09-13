package com.orchestrate.buywait.model;

import java.nio.file.Path;

/**
 * Maps strictly to dataset/images.csv.
 * Links relevant local images (receipts, bills, payslips) to users, requests, or events.
 */
public record ImageReference(
        String imageId,
        String userId,
        String requestId,
        String relatedEventId
) {
    /**
     * Resolves local image file path as specified in the problem statement:
     * dataset/media/images/<image_id>.png
     */
    public Path getImagePath(String datasetDir) {
        return Path.of(datasetDir, "media", "images", imageId + ".png");
    }
}

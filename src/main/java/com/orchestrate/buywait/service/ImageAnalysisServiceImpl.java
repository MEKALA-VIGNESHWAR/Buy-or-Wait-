package com.orchestrate.buywait.service;

import com.orchestrate.buywait.ai.ImageEvidence;
import com.orchestrate.buywait.ai.ImageEvidenceExtractor;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.ImageReference;
import com.orchestrate.buywait.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisServiceImpl.class);

    private final ImageEvidenceExtractor imageEvidenceExtractor;

    public ImageAnalysisServiceImpl(ImageEvidenceExtractor imageEvidenceExtractor) {
        this.imageEvidenceExtractor = imageEvidenceExtractor;
    }

    @Override
    public List<FinancialEvent> resolveMissingAmounts(List<FinancialEvent> events, List<ImageReference> imageReferences) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        if (imageReferences == null || imageReferences.isEmpty()) {
            return events;
        }

        Map<String, ImageReference> imageByEventId = new HashMap<>();
        for (ImageReference img : imageReferences) {
            if (img.relatedEventId() != null && !img.relatedEventId().isBlank()) {
                imageByEventId.put(img.relatedEventId(), img);
            }
        }

        List<FinancialEvent> enriched = new ArrayList<>(events.size());
        for (FinancialEvent event : events) {
            if (event.hasBlankAmount()) {
                ImageReference ref = imageByEventId.get(event.eventId());
                if (ref != null) {
                    ImageEvidence evidence = imageEvidenceExtractor.extractEvidence(ref.imageId(), event.eventId());
                    if (evidence != null && evidence.isValid()) {
                        log.info("Resolved blank amount for event {} from image {}: {} {}",
                                event.eventId(), ref.imageId(), evidence.currency(), evidence.amount());
                        FinancialEvent updated = event.withAmountAndCurrency(
                                evidence.amount(),
                                evidence.currency() != null ? evidence.currency() : event.currency()
                        );
                        enriched.add(updated);
                        continue;
                    } else {
                        log.warn("Failed to extract valid evidence for event {} from image {}", event.eventId(), ref.imageId());
                    }
                } else {
                    log.warn("Event {} has blank amount but no linked image in images.csv", event.eventId());
                }
            }
            enriched.add(event);
        }

        return enriched;
    }

    @Override
    public Map<String, ImageEvidence> extractEvidence(RequestContext context) {
        if (context == null || context.images() == null || context.images().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ImageEvidence> results = new HashMap<>();
        for (ImageReference img : context.images()) {
            ImageEvidence ev = imageEvidenceExtractor.extractEvidence(img.imageId(), img.relatedEventId());
            if (ev != null) {
                results.put(img.imageId(), ev);
            }
        }
        return results;
    }
}

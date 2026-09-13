package com.orchestrate.buywait.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orchestrate.buywait.model.ExtractedFact;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Structured container for LLM evidence extraction matching the required challenge schema:
 * {
 *   "event_updates": [...],
 *   "preferences": {...},
 *   "facts": [...]
 * }
 */
public record EvidenceExtractionResult(
        @JsonProperty("event_updates")
        List<EventUpdate> eventUpdates,

        @JsonProperty("preferences")
        Map<String, String> preferences,

        @JsonProperty("facts")
        List<ExtractedFact> facts,

        @JsonProperty("prompt_tokens")
        int promptTokens,

        @JsonProperty("completion_tokens")
        int completionTokens,

        @JsonProperty("total_tokens")
        int totalTokens,

        @JsonProperty("model_name")
        String modelName
) {
    public EvidenceExtractionResult {
        eventUpdates = eventUpdates != null ? Collections.unmodifiableList(eventUpdates) : Collections.emptyList();
        preferences = preferences != null ? Collections.unmodifiableMap(preferences) : Collections.emptyMap();
        facts = facts != null ? Collections.unmodifiableList(facts) : Collections.emptyList();
    }

    public static EvidenceExtractionResult empty() {
        return new EvidenceExtractionResult(
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyList(),
                0,
                0,
                0,
                "none"
        );
    }
}

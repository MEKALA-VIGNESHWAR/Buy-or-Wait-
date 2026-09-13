package com.orchestrate.buywait.ai;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Message;
import com.orchestrate.buywait.model.RequestContext;

import java.util.List;

/**
 * Interface for AI evidence extraction from unstructured supporting evidence.
 *
 * ARCHITECTURAL BOUNDARY:
 * The LLM is strictly an evidence extractor, NOT a financial decision-maker.
 * The LLM may interpret language and extract amendments, cancellations, confirmations,
 * delays, and preferences, but MUST NOT calculate balances or decide affordability.
 *
 * Implementations can be swapped between providers (e.g., Gemini, OpenAI, Claude,
 * or deterministic local fallback).
 */
public interface LlmEvidenceExtractor {

    /**
     * Extracts financial evidence and event updates from the messages and context of a request.
     *
     * @param requestContext full request context containing messages, events, and request info
     * @return structured EvidenceExtractionResult
     */
    EvidenceExtractionResult extractEvidence(RequestContext requestContext);

    /**
     * Extracts financial evidence for a specific financial state from a list of supporting messages.
     *
     * @param financialState reconstructed financial state
     * @param messages supporting messages associated with the user/request
     * @return structured EvidenceExtractionResult
     */
    EvidenceExtractionResult extractEvidence(FinancialState financialState, List<Message> messages);

    /**
     * Extracts financial updates from a single message given the financial state.
     *
     * @param message individual message to analyze
     * @param financialState reconstructed financial state
     * @return structured EvidenceExtractionResult
     */
    EvidenceExtractionResult extractFromMessage(Message message, FinancialState financialState);
}

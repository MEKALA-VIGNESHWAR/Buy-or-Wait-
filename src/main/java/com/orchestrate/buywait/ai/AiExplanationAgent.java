package com.orchestrate.buywait.ai;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Plan;
import com.orchestrate.buywait.model.Prediction;
import com.orchestrate.buywait.model.Request;

/**
 * Generates concise, grounded explanations for financial recommendations.
 *
 * CRITICAL ARCHITECTURE RULE:
 * The explanation agent is NOT a decision maker. It explains the deterministic decision
 * citing ONLY verified facts from FinancialState, the selected plan, and minimum balance.
 */
public interface AiExplanationAgent {

    /**
     * Generates a concise (1-3 sentence) explanation strictly grounded in verified facts.
     */
    String generateExplanation(Request request, FinancialState state, Plan selectedPlan, Prediction prediction);
}

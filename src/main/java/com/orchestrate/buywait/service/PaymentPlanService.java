package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Plan;

import java.util.List;

/**
 * Service to generate all viable candidate payment plans for a financial request.
 * Generates:
 * 1. full_payment
 * 2. partial_payment
 * 3. installments
 * 4. wait
 * 5. spending-change-assisted plans
 */
public interface PaymentPlanService {

    /**
     * Generates all safe candidate plans for the user's financial request,
     * including baseline plans and spending-change-assisted plans.
     */
    List<Plan> generateCandidatePlans(FinancialState financialState);
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.RequestContext;

public interface FinancialStateService {

    FinancialState reconstructState(RequestContext context);

    FinancialState reconstructState(String requestId);
}

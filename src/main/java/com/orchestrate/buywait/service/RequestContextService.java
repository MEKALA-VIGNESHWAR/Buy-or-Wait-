package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.Request;
import com.orchestrate.buywait.model.RequestContext;

import java.util.List;

public interface RequestContextService {

    RequestContext buildContext(Request request);

    RequestContext buildContext(String requestId);

    List<RequestContext> buildAllContexts();
}

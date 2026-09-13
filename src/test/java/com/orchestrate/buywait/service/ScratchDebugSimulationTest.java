package com.orchestrate.buywait.service;

import com.orchestrate.buywait.engine.ForecastSimulationEngine;
import com.orchestrate.buywait.model.FinancialState;
import com.orchestrate.buywait.model.Payment;
import com.orchestrate.buywait.engine.ForecastDay;
import com.orchestrate.buywait.engine.ForecastResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@SpringBootTest
public class ScratchDebugSimulationTest {

    @Autowired
    private FinancialStateService financialStateService;

    @Autowired
    private ForecastSimulationEngine forecastEngine;

    @Test
    void debugRequests() {
        FinancialState state = financialStateService.reconstructState("request_06");
        ForecastResult result = forecastEngine.simulate(state, Collections.emptyList());
        System.out.println("=== REQUEST 06 EVENTS BEFORE SALARY ===");
        for (ForecastDay day : result.dailyForecasts()) {
            if (day.date().isBefore(java.time.LocalDate.of(2026, 1, 15))) {
                for (var ev : day.appliedEvents()) {
                    System.out.println(day.date() + " " + ev.category() + " " + ev.amount() + " " + ev.description());
                }
            }
        }
    }
}

package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OutputValidatorTest {

    private OutputValidator validator;
    private Request sampleRequest;

    @BeforeEach
    void setUp() {
        validator = new OutputValidator();
        sampleRequest = new Request(
                "request_01",
                "user_01",
                LocalDate.of(2026, 1, 1),
                RequestType.purchase,
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 15),
                true,
                "Sample request text"
        );
    }

    @Test
    void testValidPredictionPasses() {
        Prediction valid = new Prediction(
                "request_01",
                new BigDecimal("1000.00"),
                AffordabilityStatus.affordable_now,
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("1000.00"))),
                LocalDate.of(2026, 1, 1),
                List.of(),
                "Pay EUR 1,000 today. Leaves at least EUR 500 available."
        );

        OutputValidator.RowValidationResult result = validator.validatePrediction(valid, sampleRequest);
        assertTrue(result.isValid(), "Expected valid prediction but got errors: " + result.errors());
    }

    @Test
    void testSafeAmountExceedingRequestedAmountFails() {
        Prediction invalid = new Prediction(
                "request_01",
                new BigDecimal("1500.00"), // exceeds 1000.00
                AffordabilityStatus.affordable_now,
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("1000.00"))),
                LocalDate.of(2026, 1, 1),
                List.of(),
                "Valid explanation"
        );

        OutputValidator.RowValidationResult result = validator.validatePrediction(invalid, sampleRequest);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("exceeds requested_amount")));
    }

    @Test
    void testAffordableNowWithWrongDateFails() {
        Prediction invalid = new Prediction(
                "request_01",
                new BigDecimal("1000.00"),
                AffordabilityStatus.affordable_now,
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("1000.00"))),
                LocalDate.of(2026, 1, 5), // Not request_date
                List.of(),
                "Valid explanation"
        );

        OutputValidator.RowValidationResult result = validator.validatePrediction(invalid, sampleRequest);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("requires earliest_date_for_full_payment to equal request_date")));
    }

    @Test
    void testExceedingMaxSpendingChangesFails() {
        List<SpendingChange> fourChanges = List.of(
                new SpendingChange(SpendingChangeType.stop, "e1", null),
                new SpendingChange(SpendingChangeType.stop, "e2", null),
                new SpendingChange(SpendingChangeType.stop, "e3", null),
                new SpendingChange(SpendingChangeType.stop, "e4", null)
        );

        Prediction invalid = new Prediction(
                "request_01",
                BigDecimal.ZERO,
                AffordabilityStatus.affordable_with_plan,
                PaymentMethod.full_payment,
                List.of(new Payment(LocalDate.of(2026, 1, 1), new BigDecimal("1000.00"))),
                LocalDate.of(2026, 1, 1),
                fourChanges,
                "Valid explanation"
        );

        OutputValidator.RowValidationResult result = validator.validatePrediction(invalid, sampleRequest);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Exceeded maximum of 3 spending changes")));
    }
}

package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class SampleRequestCsvParser {

    private final RequestCsvParser requestCsvParser;

    public SampleRequestCsvParser(RequestCsvParser requestCsvParser) {
        this.requestCsvParser = requestCsvParser;
    }

    public SampleRequest parse(CSVRecord record) {
        Request request = requestCsvParser.parse(record);

        BigDecimal amountSafeToPay = FormatUtils.parseBigDecimal(record.get("amount_safe_to_pay"));
        String statusStr = FormatUtils.cleanString(record.get("affordability_status"));
        AffordabilityStatus status = statusStr != null ? AffordabilityStatus.valueOf(statusStr) : null;

        String methodStr = FormatUtils.cleanString(record.get("recommended_payment_method"));
        PaymentMethod method = methodStr != null ? PaymentMethod.valueOf(methodStr) : null;

        List<Payment> paymentPlan = FormatUtils.parsePaymentPlan(record.get("payment_plan"));
        LocalDate earliestDate = FormatUtils.parseDate(record.get("earliest_date_for_full_payment"));
        List<SpendingChange> changes = FormatUtils.parseSpendingChanges(record.get("spending_changes_needed"));
        String explanation = FormatUtils.cleanString(record.get("decision_explanation"));

        Prediction prediction = new Prediction(
                request.requestId(),
                amountSafeToPay,
                status,
                method,
                paymentPlan,
                earliestDate,
                changes,
                explanation
        );

        return new SampleRequest(request, prediction);
    }
}

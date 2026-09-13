package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.Request;
import com.orchestrate.buywait.model.RequestType;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class RequestCsvParser {

    public Request parse(CSVRecord record) {
        String requestId = FormatUtils.cleanString(record.get("request_id"));
        String userId = FormatUtils.cleanString(record.get("user_id"));
        if (requestId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing request_id");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing user_id");
        }

        LocalDate requestDate = FormatUtils.parseDate(record.get("request_date"));
        RequestType requestType = RequestType.fromString(record.get("request_type"));
        BigDecimal requestedAmount = FormatUtils.parseBigDecimal(record.get("requested_amount"));
        LocalDate desiredCompletionDate = FormatUtils.parseDate(record.get("desired_completion_date"));
        boolean allowsPartialPayment = FormatUtils.parseBoolean(record.get("allows_partial_payment"));
        String requestText = FormatUtils.cleanString(record.get("request_text"));

        return new Request(
                requestId,
                userId,
                requestDate,
                requestType,
                requestedAmount,
                desiredCompletionDate,
                allowsPartialPayment,
                requestText
        );
    }
}

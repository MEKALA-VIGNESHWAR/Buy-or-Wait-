package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.EventDirection;
import com.orchestrate.buywait.model.EventFlexibility;
import com.orchestrate.buywait.model.EventStatus;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class FinancialEventCsvParser {

    public FinancialEvent parse(CSVRecord record) {
        String eventId = FormatUtils.cleanString(record.get("event_id"));
        String userId = FormatUtils.cleanString(record.get("user_id"));
        if (eventId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing event_id");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing user_id");
        }

        String eventType = FormatUtils.cleanString(record.get("event_type"));
        String description = FormatUtils.cleanString(record.get("description"));
        String category = FormatUtils.cleanString(record.get("category"));
        EventDirection direction = EventDirection.fromString(record.get("direction"));

        // Crucial requirement: blank amount must be null, NEVER zero
        BigDecimal amount = FormatUtils.parseBigDecimal(record.get("amount"));

        String currency = FormatUtils.cleanString(record.get("currency"));
        LocalDate eventDate = FormatUtils.parseDate(record.get("event_date"));
        LocalDate settlementDate = FormatUtils.parseDate(record.get("settlement_date"));
        EventStatus status = EventStatus.fromString(record.get("status"));
        String linkedEventId = FormatUtils.cleanString(record.get("linked_event_id"));
        EventFlexibility flexibility = EventFlexibility.fromString(record.get("flexibility"));
        BigDecimal minimumAllowedAmount = FormatUtils.parseBigDecimal(record.get("minimum_allowed_amount"));

        return new FinancialEvent(
                eventId,
                userId,
                eventType,
                description,
                category,
                direction,
                amount,
                currency,
                eventDate,
                settlementDate,
                status,
                linkedEventId,
                flexibility,
                minimumAllowedAmount
        );
    }
}

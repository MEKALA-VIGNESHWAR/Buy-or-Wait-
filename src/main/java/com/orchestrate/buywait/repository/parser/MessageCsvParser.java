package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.Message;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class MessageCsvParser {

    public Message parse(CSVRecord record) {
        String messageId = FormatUtils.cleanString(record.get("message_id"));
        String userId = FormatUtils.cleanString(record.get("user_id"));
        if (messageId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing message_id");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing user_id");
        }

        String requestId = FormatUtils.cleanString(record.get("request_id"));
        String relatedEventId = FormatUtils.cleanString(record.get("related_event_id"));
        String sentAt = FormatUtils.cleanString(record.get("sent_at"));
        String sourceType = FormatUtils.cleanString(record.get("source_type"));
        String messageText = FormatUtils.cleanString(record.get("message_text"));

        return new Message(
                messageId,
                userId,
                requestId,
                relatedEventId,
                sentAt,
                sourceType,
                messageText
        );
    }
}

package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.ImageReference;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class ImageReferenceCsvParser {

    public ImageReference parse(CSVRecord record) {
        String imageId = FormatUtils.cleanString(record.get("image_id"));
        String userId = FormatUtils.cleanString(record.get("user_id"));
        if (imageId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing image_id");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing user_id");
        }

        String requestId = FormatUtils.cleanString(record.get("request_id"));
        String relatedEventId = FormatUtils.cleanString(record.get("related_event_id"));

        return new ImageReference(
                imageId,
                userId,
                requestId,
                relatedEventId
        );
    }
}

package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.ExchangeRate;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class ExchangeRateCsvParser {

    public ExchangeRate parse(CSVRecord record) {
        LocalDate rateDate = FormatUtils.parseDate(record.get("rate_date"));
        String fromCurrency = FormatUtils.cleanString(record.get("from_currency"));
        String toCurrency = FormatUtils.cleanString(record.get("to_currency"));
        BigDecimal rate = FormatUtils.parseBigDecimal(record.get("rate"));

        if (rateDate == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing rate_date");
        }
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing currency pair");
        }
        if (rate == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing rate value");
        }

        return new ExchangeRate(
                rateDate,
                fromCurrency,
                toCurrency,
                rate
        );
    }
}

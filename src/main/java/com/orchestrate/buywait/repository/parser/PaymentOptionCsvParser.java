package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.model.PaymentOption;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class PaymentOptionCsvParser {

    public PaymentOption parse(CSVRecord record) {
        String optionId = FormatUtils.cleanString(record.get("payment_option_id"));
        String requestId = FormatUtils.cleanString(record.get("request_id"));
        if (optionId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing payment_option_id");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing request_id");
        }

        String methodStr = FormatUtils.cleanString(record.get("payment_method"));
        PaymentMethod paymentMethod = methodStr != null ? PaymentMethod.valueOf(methodStr.toLowerCase()) : null;

        BigDecimal paymentAmount = FormatUtils.parseBigDecimal(record.get("payment_amount"));
        Integer numberOfPayments = FormatUtils.parseInteger(record.get("number_of_payments"));
        LocalDate firstPaymentDate = FormatUtils.parseDate(record.get("first_payment_date"));
        Integer paymentFrequencyDays = FormatUtils.parseInteger(record.get("payment_frequency_days"));
        BigDecimal financingFee = FormatUtils.parseBigDecimal(record.get("financing_fee"));
        BigDecimal totalPayableAmount = FormatUtils.parseBigDecimal(record.get("total_payable_amount"));

        return new PaymentOption(
                optionId,
                requestId,
                paymentMethod,
                paymentAmount,
                numberOfPayments,
                firstPaymentDate,
                paymentFrequencyDays,
                financingFee,
                totalPayableAmount
        );
    }
}

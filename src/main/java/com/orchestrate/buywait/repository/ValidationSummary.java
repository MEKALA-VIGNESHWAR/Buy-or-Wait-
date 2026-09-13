package com.orchestrate.buywait.repository;

public record ValidationSummary(
        int requestCount,
        int sampleRequestCount,
        int profileCount,
        int financialEventCount,
        int paymentOptionCount,
        int messageCount,
        int imageCount,
        int exchangeRateCount
) {
    public String toReportString() {
        return String.format("""
                ================================================================================
                  DATASET INGESTION & STARTUP VALIDATION REPORT
                ================================================================================
                  Requests (dataset/requests.csv)                 : %,d
                  Sample Requests (dataset/sample_requests.csv)   : %,d
                  Financial Profiles (dataset/financial_profiles.csv): %,d
                  Financial Events (dataset/financial_events.csv) : %,d
                  Payment Options (dataset/request_payment_options.csv): %,d
                  Messages (dataset/messages.csv)                 : %,d
                  Images (dataset/images.csv)                     : %,d
                  Exchange Rates (dataset/exchange_rates.csv)     : %,d
                ================================================================================
                """,
                requestCount,
                sampleRequestCount,
                profileCount,
                financialEventCount,
                paymentOptionCount,
                messageCount,
                imageCount,
                exchangeRateCount
        );
    }
}

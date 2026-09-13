package com.orchestrate.buywait.repository;

import com.orchestrate.buywait.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialDataRepository {

    void initialize();

    ValidationSummary getValidationSummary();

    List<Request> getAllRequests();

    Optional<Request> findRequestById(String requestId);

    List<SampleRequest> getAllSampleRequests();

    List<FinancialProfile> getAllProfiles();

    Optional<FinancialProfile> findProfileByUserId(String userId);

    List<FinancialEvent> findEventsByUserId(String userId);

    Optional<FinancialEvent> findEventById(String eventId);

    List<PaymentOption> findPaymentOptionsByRequestId(String requestId);

    List<Message> findMessagesByUserId(String userId);

    List<Message> findMessagesByRequestId(String requestId);

    List<Message> findMessagesByEventId(String eventId);

    Optional<ImageReference> findImageById(String imageId);

    List<ImageReference> findImagesByUserId(String userId);

    List<ImageReference> findImagesByRequestId(String requestId);

    List<ImageReference> findImagesByEventId(String eventId);

    Optional<ExchangeRate> findExchangeRate(LocalDate date, String fromCurrency, String toCurrency);

    List<ExchangeRate> getAllExchangeRates();

    void writeOutput(List<Prediction> predictions);
}

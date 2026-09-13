package com.orchestrate.buywait.repository;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.parser.*;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

@Repository
public class CsvFinancialDataRepository implements FinancialDataRepository {

    private static final Logger log = LoggerFactory.getLogger(CsvFinancialDataRepository.class);

    @Value("${app.dataset.dir:dataset}")
    private String datasetDir;

    @Value("${app.output.file:output.csv}")
    private String outputFile;

    private final RequestCsvParser requestCsvParser;
    private final SampleRequestCsvParser sampleRequestCsvParser;
    private final FinancialProfileCsvParser financialProfileCsvParser;
    private final FinancialEventCsvParser financialEventCsvParser;
    private final PaymentOptionCsvParser paymentOptionCsvParser;
    private final MessageCsvParser messageCsvParser;
    private final ImageReferenceCsvParser imageReferenceCsvParser;
    private final ExchangeRateCsvParser exchangeRateCsvParser;

    // Preserved deterministic lists
    private final List<Request> requests = new ArrayList<>();
    private final List<SampleRequest> sampleRequests = new ArrayList<>();
    private final List<FinancialProfile> profiles = new ArrayList<>();
    private final List<FinancialEvent> financialEvents = new ArrayList<>();
    private final List<PaymentOption> paymentOptions = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();
    private final List<ImageReference> images = new ArrayList<>();
    private final List<ExchangeRate> exchangeRates = new ArrayList<>();

    // Indexed Lookups
    private final Map<String, Request> requestsById = new LinkedHashMap<>();
    private final Map<String, SampleRequest> sampleRequestsById = new LinkedHashMap<>();
    private final Map<String, FinancialProfile> profilesByUserId = new LinkedHashMap<>();
    private final Map<String, List<FinancialEvent>> eventsByUserId = new LinkedHashMap<>();
    private final Map<String, FinancialEvent> eventsById = new LinkedHashMap<>();
    private final Map<String, List<PaymentOption>> paymentOptionsByRequestId = new LinkedHashMap<>();
    private final Map<String, List<Message>> messagesByUserId = new LinkedHashMap<>();
    private final Map<String, List<Message>> messagesByRequestId = new LinkedHashMap<>();
    private final Map<String, List<Message>> messagesByEventId = new LinkedHashMap<>();
    private final Map<String, ImageReference> imagesById = new LinkedHashMap<>();
    private final Map<String, List<ImageReference>> imagesByUserId = new LinkedHashMap<>();
    private final Map<String, List<ImageReference>> imagesByRequestId = new LinkedHashMap<>();
    private final Map<String, List<ImageReference>> imagesByEventId = new LinkedHashMap<>();
    private final Map<String, ExchangeRate> exchangeRatesByDateAndPair = new LinkedHashMap<>();

    private ValidationSummary validationSummary;

    private static final String[] OUTPUT_HEADERS = {
            "request_id",
            "amount_safe_to_pay",
            "affordability_status",
            "recommended_payment_method",
            "payment_plan",
            "earliest_date_for_full_payment",
            "spending_changes_needed",
            "decision_explanation"
    };

    public CsvFinancialDataRepository(
            RequestCsvParser requestCsvParser,
            SampleRequestCsvParser sampleRequestCsvParser,
            FinancialProfileCsvParser financialProfileCsvParser,
            FinancialEventCsvParser financialEventCsvParser,
            PaymentOptionCsvParser paymentOptionCsvParser,
            MessageCsvParser messageCsvParser,
            ImageReferenceCsvParser imageReferenceCsvParser,
            ExchangeRateCsvParser exchangeRateCsvParser
    ) {
        this.requestCsvParser = requestCsvParser;
        this.sampleRequestCsvParser = sampleRequestCsvParser;
        this.financialProfileCsvParser = financialProfileCsvParser;
        this.financialEventCsvParser = financialEventCsvParser;
        this.paymentOptionCsvParser = paymentOptionCsvParser;
        this.messageCsvParser = messageCsvParser;
        this.imageReferenceCsvParser = imageReferenceCsvParser;
        this.exchangeRateCsvParser = exchangeRateCsvParser;
    }

    @PostConstruct
    @Override
    public void initialize() {
        log.info("Loading financial dataset from: {}", Path.of(datasetDir).toAbsolutePath());
        loadRequests();
        loadSampleRequests();
        loadFinancialProfiles();
        loadFinancialEvents();
        loadPaymentOptions();
        loadMessages();
        loadImages();
        loadExchangeRatesInternal();

        this.validationSummary = new ValidationSummary(
                requests.size(),
                sampleRequests.size(),
                profiles.size(),
                financialEvents.size(),
                paymentOptions.size(),
                messages.size(),
                images.size(),
                exchangeRates.size()
        );

        log.info("\n{}", validationSummary.toReportString());
    }

    private void loadRequests() {
        Path path = Path.of(datasetDir, "requests.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                Request request = requestCsvParser.parse(record);
                requests.add(request);
                requestsById.put(request.requestId(), request);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load requests from " + path, e);
        }
    }

    private void loadSampleRequests() {
        Path path = Path.of(datasetDir, "sample_requests.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                SampleRequest sample = sampleRequestCsvParser.parse(record);
                sampleRequests.add(sample);
                sampleRequestsById.put(sample.request().requestId(), sample);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sample requests from " + path, e);
        }
    }

    private void loadFinancialProfiles() {
        Path path = Path.of(datasetDir, "financial_profiles.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                FinancialProfile profile = financialProfileCsvParser.parse(record);
                profiles.add(profile);
                profilesByUserId.put(profile.userId(), profile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load financial profiles from " + path, e);
        }
    }

    private void loadFinancialEvents() {
        Path path = Path.of(datasetDir, "financial_events.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                FinancialEvent event = financialEventCsvParser.parse(record);
                financialEvents.add(event);
                eventsById.put(event.eventId(), event);
                eventsByUserId.computeIfAbsent(event.userId(), k -> new ArrayList<>()).add(event);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load financial events from " + path, e);
        }
    }

    private void loadPaymentOptions() {
        Path path = Path.of(datasetDir, "request_payment_options.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                PaymentOption option = paymentOptionCsvParser.parse(record);
                paymentOptions.add(option);
                paymentOptionsByRequestId.computeIfAbsent(option.requestId(), k -> new ArrayList<>()).add(option);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load payment options from " + path, e);
        }
    }

    private void loadMessages() {
        Path path = Path.of(datasetDir, "messages.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                Message message = messageCsvParser.parse(record);
                messages.add(message);
                messagesByUserId.computeIfAbsent(message.userId(), k -> new ArrayList<>()).add(message);
                if (message.requestId() != null) {
                    messagesByRequestId.computeIfAbsent(message.requestId(), k -> new ArrayList<>()).add(message);
                }
                if (message.relatedEventId() != null) {
                    messagesByEventId.computeIfAbsent(message.relatedEventId(), k -> new ArrayList<>()).add(message);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load messages from " + path, e);
        }
    }

    private void loadImages() {
        Path path = Path.of(datasetDir, "images.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                ImageReference image = imageReferenceCsvParser.parse(record);
                images.add(image);
                imagesById.put(image.imageId(), image);
                imagesByUserId.computeIfAbsent(image.userId(), k -> new ArrayList<>()).add(image);
                if (image.requestId() != null) {
                    imagesByRequestId.computeIfAbsent(image.requestId(), k -> new ArrayList<>()).add(image);
                }
                if (image.relatedEventId() != null) {
                    imagesByEventId.computeIfAbsent(image.relatedEventId(), k -> new ArrayList<>()).add(image);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load images from " + path, e);
        }
    }

    private void loadExchangeRatesInternal() {
        Path path = Path.of(datasetDir, "exchange_rates.csv");
        if (!Files.exists(path)) {
            log.warn("File not found: {}", path.toAbsolutePath());
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : csvParser) {
                ExchangeRate rate = exchangeRateCsvParser.parse(record);
                exchangeRates.add(rate);
                String key = buildExchangeRateKey(rate.rateDate(), rate.fromCurrency(), rate.toCurrency());
                exchangeRatesByDateAndPair.put(key, rate);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load exchange rates from " + path, e);
        }
    }

    private String buildExchangeRateKey(LocalDate date, String from, String to) {
        return (date != null ? date.toString() : "") + ":" + (from != null ? from.toUpperCase() : "") + ":" + (to != null ? to.toUpperCase() : "");
    }

    @Override
    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    @Override
    public List<Request> getAllRequests() {
        return Collections.unmodifiableList(requests);
    }

    @Override
    public Optional<Request> findRequestById(String requestId) {
        Request req = requestsById.get(requestId);
        if (req == null) {
            SampleRequest sample = sampleRequestsById.get(requestId);
            if (sample != null) {
                req = sample.request();
            }
        }
        return Optional.ofNullable(req);
    }

    @Override
    public List<SampleRequest> getAllSampleRequests() {
        return Collections.unmodifiableList(sampleRequests);
    }

    @Override
    public List<FinancialProfile> getAllProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    @Override
    public Optional<FinancialProfile> findProfileByUserId(String userId) {
        return Optional.ofNullable(profilesByUserId.get(userId));
    }

    @Override
    public List<FinancialEvent> findEventsByUserId(String userId) {
        return Collections.unmodifiableList(eventsByUserId.getOrDefault(userId, Collections.emptyList()));
    }

    @Override
    public Optional<FinancialEvent> findEventById(String eventId) {
        return Optional.ofNullable(eventsById.get(eventId));
    }

    @Override
    public List<PaymentOption> findPaymentOptionsByRequestId(String requestId) {
        return Collections.unmodifiableList(paymentOptionsByRequestId.getOrDefault(requestId, Collections.emptyList()));
    }

    @Override
    public List<Message> findMessagesByUserId(String userId) {
        return Collections.unmodifiableList(messagesByUserId.getOrDefault(userId, Collections.emptyList()));
    }

    @Override
    public List<Message> findMessagesByRequestId(String requestId) {
        return Collections.unmodifiableList(messagesByRequestId.getOrDefault(requestId, Collections.emptyList()));
    }

    @Override
    public List<Message> findMessagesByEventId(String eventId) {
        return Collections.unmodifiableList(messagesByEventId.getOrDefault(eventId, Collections.emptyList()));
    }

    @Override
    public Optional<ImageReference> findImageById(String imageId) {
        return Optional.ofNullable(imagesById.get(imageId));
    }

    @Override
    public List<ImageReference> findImagesByUserId(String userId) {
        return Collections.unmodifiableList(imagesByUserId.getOrDefault(userId, Collections.emptyList()));
    }

    @Override
    public List<ImageReference> findImagesByRequestId(String requestId) {
        return Collections.unmodifiableList(imagesByRequestId.getOrDefault(requestId, Collections.emptyList()));
    }

    @Override
    public List<ImageReference> findImagesByEventId(String eventId) {
        return Collections.unmodifiableList(imagesByEventId.getOrDefault(eventId, Collections.emptyList()));
    }

    @Override
    public Optional<ExchangeRate> findExchangeRate(LocalDate date, String fromCurrency, String toCurrency) {
        String key = buildExchangeRateKey(date, fromCurrency, toCurrency);
        return Optional.ofNullable(exchangeRatesByDateAndPair.get(key));
    }

    @Override
    public List<ExchangeRate> getAllExchangeRates() {
        return Collections.unmodifiableList(exchangeRates);
    }

    @Override
    public void writeOutput(List<Prediction> predictions) {
        Path path = Path.of(outputFile);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADERS).build())) {
            for (Prediction res : predictions) {
                printer.printRecord(
                        res.requestId(),
                        res.formattedAmountSafeToPay(),
                        res.affordabilityStatus() != null ? res.affordabilityStatus().name() : "",
                        res.recommendedPaymentMethod() != null ? res.recommendedPaymentMethod().name() : "",
                        res.formattedPaymentPlan(),
                        res.formattedEarliestDate(),
                        res.formattedSpendingChanges(),
                        res.decisionExplanation() != null ? res.decisionExplanation() : ""
                );
            }
            log.info("Wrote {} predictions to {}", predictions.size(), path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write predictions to output.csv", e);
            throw new RuntimeException("Error writing output.csv", e);
        }
    }
}

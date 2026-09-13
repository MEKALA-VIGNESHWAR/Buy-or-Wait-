package com.orchestrate.buywait.repository;

import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CsvFinancialDataRepositoryTest {

    @Autowired
    private FinancialDataRepository repository;

    @Test
    void testIngestionCounts() {
        ValidationSummary summary = repository.getValidationSummary();
        assertNotNull(summary);

        assertEquals(250, summary.requestCount(), "requests.csv should have 250 requests");
        assertEquals(25, summary.sampleRequestCount(), "sample_requests.csv should have 25 sample requests");
        assertEquals(275, summary.profileCount(), "financial_profiles.csv should have 275 profiles");
        assertEquals(25342, summary.financialEventCount(), "financial_events.csv should have 25342 events");
        assertEquals(790, summary.paymentOptionCount(), "request_payment_options.csv should have 790 options");
        assertEquals(215, summary.messageCount(), "messages.csv should have 215 messages");
        assertEquals(16, summary.imageCount(), "images.csv should have 16 images");
        assertEquals(134, summary.exchangeRateCount(), "exchange_rates.csv should have 134 rates");
    }

    @Test
    void testBlankAmountsAreNullNotZero() {
        // Event 253 is known to have a blank amount in financial_events.csv
        var eventOpt = repository.findEventById("event_253");
        assertTrue(eventOpt.isPresent(), "event_253 should exist");
        assertNull(eventOpt.get().amount(), "Blank amount in financial_events.csv must be parsed as null, NOT zero");
    }

    @Test
    void testIndexedLookups() {
        // Test request lookup
        var reqOpt = repository.findRequestById("request_26");
        assertTrue(reqOpt.isPresent());
        Request req = reqOpt.get();
        assertEquals("user_26", req.userId());

        // Test profile lookup
        var profileOpt = repository.findProfileByUserId("user_26");
        assertTrue(profileOpt.isPresent());
        assertEquals("IDR", profileOpt.get().homeCurrency());

        // Test event lookups by user
        List<FinancialEvent> userEvents = repository.findEventsByUserId("user_26");
        assertFalse(userEvents.isEmpty(), "user_26 should have financial events");

        // Test image reference lookup
        var imageOpt = repository.findImageById("image_01");
        assertTrue(imageOpt.isPresent());
        assertEquals("event_253", imageOpt.get().relatedEventId());
    }
}

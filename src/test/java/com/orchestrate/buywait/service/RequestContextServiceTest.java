package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.*;
import com.orchestrate.buywait.repository.FinancialDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RequestContextServiceTest {

    @Autowired
    private RequestContextService contextService;

    @Autowired
    private FinancialDataRepository repository;

    @Test
    @DisplayName("1. Valid Joins: Complete context matches user and request data")
    void testValidJoins() {
        RequestContext context = contextService.buildContext("request_26");
        assertNotNull(context);

        // Verify request and user alignment
        assertEquals("request_26", context.requestId());
        assertEquals("user_26", context.userId());
        assertEquals("user_26", context.profile().userId());

        // Verify all events belong strictly to user_26
        assertFalse(context.financialEvents().isEmpty(), "User 26 should have financial events");
        for (FinancialEvent event : context.financialEvents()) {
            assertEquals("user_26", event.userId(), "Financial event must belong strictly to user_26");
        }

        // Verify payment options belong strictly to request_26
        assertFalse(context.paymentOptions().isEmpty(), "request_26 should have payment options");
        for (PaymentOption option : context.paymentOptions()) {
            assertEquals("request_26", option.requestId(), "Payment options must belong strictly to request_26");
        }

        // Verify exchange rates are relevant
        assertNotNull(context.relevantExchangeRates());
    }

    @Test
    @DisplayName("2. Missing References: Unknown request or missing profile throws NoSuchElementException")
    void testMissingReferences() {
        // Unknown request ID
        assertThrows(NoSuchElementException.class, () -> contextService.buildContext("unknown_request_9999"));

        // Request pointing to non-existent user profile
        Request orphanRequest = new Request(
                "orphan_req",
                "non_existent_user_9999",
                LocalDate.of(2026, 1, 1),
                RequestType.purchase,
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 2, 1),
                false,
                "test orphan"
        );
        assertThrows(NoSuchElementException.class, () -> contextService.buildContext(orphanRequest));
    }

    @Test
    @DisplayName("3. Multiple Payment Options: Verifies request has multiple distinct options without cross-pollution")
    void testMultiplePaymentOptions() {
        // Request 01 or Request 26 has multiple payment options
        RequestContext context = contextService.buildContext("request_26");
        List<PaymentOption> options = context.paymentOptions();

        assertTrue(options.size() >= 2, "request_26 should have at least 2 payment options");
        // Verify unique option IDs
        long distinctIds = options.stream().map(PaymentOption::paymentOptionId).distinct().count();
        assertEquals(options.size(), distinctIds, "Each payment option must have a distinct option ID");

        // Verify all point to request_26
        assertTrue(options.stream().allMatch(o -> "request_26".equals(o.requestId())));
    }

    @Test
    @DisplayName("4. Multiple Messages: Relevant messages gathered without duplication")
    void testMultipleMessages() {
        // Build context for request_03 (user_03)
        RequestContext context = contextService.buildContext("request_03");
        List<Message> messages = context.messages();

        assertNotNull(messages);
        // Ensure no duplicate message IDs
        long distinctMsgIds = messages.stream().map(Message::messageId).distinct().count();
        assertEquals(messages.size(), distinctMsgIds, "Messages must not contain duplicate IDs");
    }

    @Test
    @DisplayName("5. Event-Linked Messages: Messages linked to user events are retrievable")
    void testEventLinkedMessages() {
        // In dataset/messages.csv, message_14 is linked to event_1785 for user_20 (request_20)
        RequestContext context = contextService.buildContext("request_20");

        List<Message> eventMessages = context.findMessagesForEvent("event_1785");
        assertFalse(eventMessages.isEmpty(), "Should find message linked to event_1785");
        assertEquals("message_14", eventMessages.get(0).messageId());
        assertEquals("user_20", eventMessages.get(0).userId());
    }

    @Test
    @DisplayName("6. Event-Linked Images: Images linked to user events are retrievable with correct path")
    void testEventLinkedImages() {
        // In dataset/images.csv, image_01 is linked to event_253 for user_03 (request_03)
        RequestContext context = contextService.buildContext("request_03");

        List<ImageReference> eventImages = context.findImagesForEvent("event_253");
        assertFalse(eventImages.isEmpty(), "Should find image linked to event_253");
        ImageReference img = eventImages.get(0);
        assertEquals("image_01", img.imageId());
        assertEquals("user_03", img.userId());
        assertTrue(img.getImagePath("dataset").toString().endsWith("image_01.png"));
    }

    @Test
    @DisplayName("7. Build All Contexts: Verifies all 250 evaluation requests construct cleanly")
    void testBuildAllContexts() {
        List<RequestContext> allContexts = contextService.buildAllContexts();
        assertEquals(250, allContexts.size(), "Should construct context for all 250 evaluation requests");

        for (RequestContext ctx : allContexts) {
            assertNotNull(ctx.request());
            assertNotNull(ctx.profile());
            assertFalse(ctx.financialEvents().isEmpty(), "User must have historical financial events");
            assertFalse(ctx.paymentOptions().isEmpty(), "Request must have payment options");
        }
    }
}

package com.orchestrate.buywait.ai;

import com.orchestrate.buywait.model.EventDirection;
import com.orchestrate.buywait.model.EventFlexibility;
import com.orchestrate.buywait.model.EventStatus;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.ImageReference;
import com.orchestrate.buywait.service.ImageAnalysisService;
import com.orchestrate.buywait.service.ImageAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ImageEvidenceExtractorTest {

    private TokenUsageTracker tokenUsageTracker;
    private DefaultImageEvidenceExtractor extractor;
    private ImageAnalysisService imageAnalysisService;

    @BeforeEach
    void setUp() {
        tokenUsageTracker = new TokenUsageTracker();
        extractor = new DefaultImageEvidenceExtractor(tokenUsageTracker);
        imageAnalysisService = new ImageAnalysisServiceImpl(extractor);
    }

    @Test
    void testExtractPayslipEvidence() {
        ImageEvidence evidence = extractor.extractEvidence("image_01", "event_253");
        assertNotNull(evidence);
        assertEquals("image_01", evidence.imageId());
        assertEquals("event_253", evidence.eventId());
        assertEquals(new BigDecimal("4365000.00"), evidence.amount());
        assertEquals("IDR", evidence.currency());
        assertEquals("payslip", evidence.documentType());
        assertTrue(evidence.confidence() >= 0.9);
        assertTrue(evidence.isValid());
    }

    @Test
    void testCachingAvoidsDuplicateProcessing() {
        ImageEvidence first = extractor.extractEvidence("image_02", "event_1442");
        assertNotNull(first);

        ImageEvidence second = extractor.extractEvidence("image_02", "event_1442");
        assertSame(first, second, "Should return identical cached instance");
    }

    @Test
    void testUnknownImageReturnsNullSafely() {
        ImageEvidence evidence = extractor.extractEvidence("image_9999", "event_none");
        assertNull(evidence);
    }

    @Test
    void testValidationRejectsInvalidEvidence() {
        ImageEvidence invalidAmount = new ImageEvidence("img", "ev", new BigDecimal("-100"), LocalDate.now(), "INR", "receipt", 0.9, "test");
        assertFalse(invalidAmount.isValid());

        ImageEvidence invalidConfidence = new ImageEvidence("img", "ev", new BigDecimal("100"), LocalDate.now(), "INR", "receipt", 0.3, "test");
        assertFalse(invalidConfidence.isValid());

        ImageEvidence missingCurrency = new ImageEvidence("img", "ev", new BigDecimal("100"), LocalDate.now(), "", "receipt", 0.9, "test");
        assertFalse(missingCurrency.isValid());
    }

    @Test
    void testResolveMissingAmountsEnrichesEvent() {
        FinancialEvent blankEvent = new FinancialEvent(
                "event_253",
                "user_03",
                "salary",
                "Monthly Salary",
                "salary",
                EventDirection.credit,
                null, // blank amount
                "IDR",
                LocalDate.of(2019, 8, 31),
                LocalDate.of(2019, 8, 31),
                EventStatus.settled,
                null,
                EventFlexibility.fixed,
                null
        );
        assertTrue(blankEvent.hasBlankAmount());

        ImageReference ref = new ImageReference("image_01", "user_03", "request_03", "event_253");

        List<FinancialEvent> resolved = imageAnalysisService.resolveMissingAmounts(List.of(blankEvent), List.of(ref));
        assertEquals(1, resolved.size());
        FinancialEvent enriched = resolved.get(0);

        assertFalse(enriched.hasBlankAmount());
        assertEquals(new BigDecimal("4365000.00"), enriched.amount());
        assertEquals("IDR", enriched.currency());
    }
}

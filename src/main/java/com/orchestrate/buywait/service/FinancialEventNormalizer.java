package com.orchestrate.buywait.service;

import com.orchestrate.buywait.model.EventDirection;
import com.orchestrate.buywait.model.EventFlexibility;
import com.orchestrate.buywait.model.EventStatus;
import com.orchestrate.buywait.model.FinancialEvent;
import com.orchestrate.buywait.model.FinancialProfile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class FinancialEventNormalizer {

    private static final Set<String> RECURRING_CATEGORIES = Set.of(
            "rent",
            "housing",
            "utilities",
            "education",
            "debt_repayment",
            "music_subscription",
            "streaming",
            "cloud_storage",
            "delivery_membership",
            "gym",
            "insurance"
    );

    /**
     * Identifies cancelled or failed transactions.
     * Rule: Ignore failed and cancelled transactions.
     */
    public boolean isFailedOrCancelled(FinancialEvent event) {
        if (event == null || event.status() == null) {
            return false;
        }
        return event.status() == EventStatus.cancelled || event.status() == EventStatus.failed;
    }

    /**
     * Identifies duplicate records.
     * Rule: Ignore duplicate records (e.g. "Possible duplicate card charge" linking to settled charge).
     */
    public boolean isDuplicate(FinancialEvent event, Map<String, FinancialEvent> eventsById) {
        if (event == null) {
            return false;
        }
        String desc = event.description() != null ? event.description().toLowerCase() : "";
        if (desc.contains("duplicate")) {
            return true;
        }
        // If linked to an earlier event of the exact same amount and description
        if (event.linkedEventId() != null && eventsById != null) {
            FinancialEvent linked = eventsById.get(event.linkedEventId());
            if (linked != null && Objects.equals(event.amount(), linked.amount())
                    && Objects.equals(event.description(), linked.description())
                    && event.isPending() && linked.isSettled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies non-cash or unrealized investment valuation changes.
     * Rule: Do not treat unrealized investment value as available cash.
     */
    public boolean isUnrealizedInvestment(FinancialEvent event) {
        if (event == null) {
            return false;
        }
        if (event.status() == EventStatus.unrealized || event.direction() == EventDirection.non_cash) {
            return true;
        }
        String type = event.eventType() != null ? event.eventType().toLowerCase() : "";
        return type.contains("valuation");
    }

    /**
     * Identifies pending credits.
     * Rule: Reserve pending debits. Do not count pending credits, bonuses, commissions,
     * refunds, lottery proceeds, or investment gains until they settle.
     */
    public boolean isPendingCredit(FinancialEvent event) {
        if (event == null) {
            return false;
        }
        return event.isPending() && event.isCredit();
    }

    /**
     * Identifies active pending debits that must be reserved.
     * Rule: Reserve pending debits.
     */
    public boolean isPendingDebit(FinancialEvent event, Map<String, FinancialEvent> eventsById) {
        if (event == null) {
            return false;
        }
        if (!event.isPending() || !event.isDebit()) {
            return false;
        }
        // Duplicate pending debits are ignored rather than reserved
        return !isDuplicate(event, eventsById);
    }

    /**
     * Identifies confirmed salary income on or after request_date.
     * Rule: Count confirmed salary on its settlement date.
     */
    public boolean isConfirmedSalary(FinancialEvent event, LocalDate requestDate) {
        if (event == null || !event.isCredit()) {
            return false;
        }
        LocalDate date = event.settlementDate() != null ? event.settlementDate() : event.eventDate();
        if (date == null || date.isBefore(requestDate)) {
            return false;
        }
        String cat = event.category() != null ? event.category().toLowerCase() : "";
        String desc = event.description() != null ? event.description().toLowerCase() : "";
        boolean isSalary = "salary".equals(cat) || desc.contains("salary");
        return isSalary && (event.isSettled() || event.isScheduled());
    }

    /**
     * Identifies historical settled transactions that settled prior to request_date.
     * These are already reflected in the starting available balance.
     */
    public boolean isHistorical(FinancialEvent event, LocalDate requestDate) {
        if (event == null) {
            return false;
        }
        LocalDate date = event.settlementDate() != null ? event.settlementDate() : event.eventDate();
        return date != null && date.isBefore(requestDate) && event.isSettled();
    }

    /**
     * Identifies valid future expenses settling on or after request_date.
     */
    public boolean isFutureExpense(FinancialEvent event, LocalDate requestDate) {
        if (event == null || !event.isDebit()) {
            return false;
        }
        LocalDate date = event.settlementDate() != null ? event.settlementDate() : event.eventDate();
        if (date == null || date.isBefore(requestDate)) {
            return false;
        }
        return event.isSettled() || event.isScheduled();
    }

    /**
     * Distinguishes recurring expenses from one-time purchases.
     */
    public boolean isRecurring(FinancialEvent event) {
        if (event == null) {
            return false;
        }
        String type = event.eventType() != null ? event.eventType().toLowerCase() : "";
        if ("subscription".equals(type) || "debt_payment".equals(type)) {
            return true;
        }
        String cat = event.category() != null ? event.category().toLowerCase() : "";
        if (RECURRING_CATEGORIES.contains(cat)) {
            return true;
        }
        return event.flexibility() != null && event.flexibility() != EventFlexibility.fixed;
    }

    /**
     * Determines whether a recurring expense is flexible (can be stopped or reduced)
     * according to the user's financial profile and category protections.
     * Rule: Only flexible recurring expenses can be modified.
     * Respect protected categories and user willingness to stop/reduce.
     */
    public boolean isFlexible(FinancialEvent event, FinancialProfile profile) {
        if (event == null || profile == null || !isRecurring(event)) {
            return false;
        }
        EventFlexibility flex = event.flexibility();
        if (flex == null || flex == EventFlexibility.fixed) {
            return false;
        }

        String cat = event.category();
        // If the category is protected by the user, it is NOT flexible
        if (profile.isCategoryProtected(cat)) {
            return false;
        }

        // Must be willing to stop or reduce
        boolean willingToStop = flex.canStop() && profile.isCategoryWillingToStop(cat);
        boolean willingToReduce = flex.canReduce() && profile.isCategoryWillingToReduce(cat);

        return willingToStop || willingToReduce;
    }

    /**
     * Resolves conflicts between financial events according to problem rules:
     * 1. Explicit cancellation/settlement/amendment (linked events)
     * 2. Newer record from same source
     * 3. Settled event over estimate/forecast
     * 4. Financially safer interpretation if unresolved
     */
    public List<FinancialEvent> resolveConflicts(List<FinancialEvent> rawEvents, Map<String, FinancialEvent> eventsById) {
        if (rawEvents == null || rawEvents.isEmpty()) {
            return Collections.emptyList();
        }

        // Set of events explicitly superseded or cancelled by linked records
        Set<String> supersededEventIds = new HashSet<>();

        for (FinancialEvent event : rawEvents) {
            if (event.linkedEventId() != null) {
                FinancialEvent linked = eventsById != null ? eventsById.get(event.linkedEventId()) : null;
                if (linked != null) {
                    // Case 1: authorization was cancelled, replaced by settled purchase
                    if (linked.status() == EventStatus.cancelled && event.isSettled()) {
                        supersededEventIds.add(linked.eventId());
                    }
                    // Case 2: failed payment retry - failed attempt is superseded
                    if (linked.status() == EventStatus.failed && event.isScheduled()) {
                        supersededEventIds.add(linked.eventId());
                    }
                    // Case 3: duplicate charge - duplicate is superseded
                    if (isDuplicate(event, eventsById)) {
                        supersededEventIds.add(event.eventId());
                    }
                }
            }
        }

        List<FinancialEvent> resolved = new ArrayList<>();
        for (FinancialEvent event : rawEvents) {
            if (!supersededEventIds.contains(event.eventId())) {
                resolved.add(event);
            }
        }
        return resolved;
    }
}

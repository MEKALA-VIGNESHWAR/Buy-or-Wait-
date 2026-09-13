package com.orchestrate.buywait.util;

import com.orchestrate.buywait.model.Payment;
import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.model.SpendingChange;
import com.orchestrate.buywait.model.SpendingChangeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FormatUtils {

    private FormatUtils() {
    }

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String cleanString(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static LocalDate parseDate(String text) {
        String clean = cleanString(text);
        if (clean == null) {
            return null;
        }
        try {
            return LocalDate.parse(clean, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: '" + text + "', expected yyyy-MM-dd", e);
        }
    }

    public static BigDecimal parseBigDecimal(String text) {
        String clean = cleanString(text);
        if (clean == null) {
            return null;
        }
        String stripped = clean.replace(",", "").replace(" ", "");
        try {
            return new BigDecimal(stripped).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid monetary amount: '" + text + "'", e);
        }
    }

    public static Integer parseInteger(String text) {
        String clean = cleanString(text);
        if (clean == null) {
            return null;
        }
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: '" + text + "'", e);
        }
    }

    public static boolean parseBoolean(String text) {
        String clean = cleanString(text);
        return clean != null && Boolean.parseBoolean(clean);
    }

    public static List<String> parseDelimitedList(String text, String regexDelimiter) {
        String clean = cleanString(text);
        if (clean == null) {
            return Collections.emptyList();
        }
        String[] parts = clean.split(regexDelimiter);
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty()) {
                list.add(item);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static List<PaymentMethod> parsePaymentMethodList(String text) {
        List<String> raw = parseDelimitedList(text, "\\|");
        List<PaymentMethod> methods = new ArrayList<>();
        for (String s : raw) {
            try {
                methods.add(PaymentMethod.valueOf(s.trim().toLowerCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Collections.unmodifiableList(methods);
    }

    public static List<Payment> parsePaymentPlan(String text) {
        String clean = cleanString(text);
        if (clean == null || "none".equalsIgnoreCase(clean)) {
            return Collections.emptyList();
        }
        List<Payment> payments = new ArrayList<>();
        String[] entries = clean.split("\\|");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length >= 2) {
                LocalDate date = parseDate(parts[0]);
                BigDecimal amount = parseBigDecimal(parts[1]);
                payments.add(new Payment(date, amount));
            }
        }
        return Collections.unmodifiableList(payments);
    }

    public static List<SpendingChange> parseSpendingChanges(String text) {
        String clean = cleanString(text);
        if (clean == null || "none".equalsIgnoreCase(clean)) {
            return Collections.emptyList();
        }
        List<SpendingChange> changes = new ArrayList<>();
        String[] entries = clean.split("\\|");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2 && "stop".equalsIgnoreCase(parts[0].trim())) {
                changes.add(SpendingChange.stop(parts[1].trim()));
            } else if (parts.length == 3 && "reduce_to".equalsIgnoreCase(parts[0].trim())) {
                changes.add(SpendingChange.reduceTo(parts[1].trim(), parseBigDecimal(parts[2])));
            }
        }
        return Collections.unmodifiableList(changes);
    }
}

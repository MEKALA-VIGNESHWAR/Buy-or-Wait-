package com.orchestrate.buywait.model;

import java.util.Collections;
import java.util.List;

/**
 * Result of plan or prediction verification by {@code PlanVerifier}.
 * Contains a boolean validity flag and a list of all detected violation messages.
 */
public record VerificationResult(
        boolean valid,
        List<String> violations
) {
    public VerificationResult {
        violations = violations != null ? Collections.unmodifiableList(violations) : Collections.emptyList();
    }

    public static VerificationResult ok() {
        return new VerificationResult(true, Collections.emptyList());
    }

    public static VerificationResult validResult() {
        return new VerificationResult(true, Collections.emptyList());
    }

    public static VerificationResult invalid(List<String> violations) {
        return new VerificationResult(false, violations);
    }

    public static VerificationResult invalid(String violation) {
        return new VerificationResult(false, List.of(violation));
    }

    public boolean hasViolations() {
        return !valid || !violations.isEmpty();
    }

    public boolean isValid() {
        return valid;
    }
}

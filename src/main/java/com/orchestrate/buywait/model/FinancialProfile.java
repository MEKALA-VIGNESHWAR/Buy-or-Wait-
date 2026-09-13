package com.orchestrate.buywait.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps strictly to dataset/financial_profiles.csv.
 * Represents user-specific financial position, risk thresholds, and preferences.
 */
public record FinancialProfile(
        String userId,
        String homeCurrency,
        BigDecimal currentAvailableBalance,
        BigDecimal minimumBalanceToKeep,
        List<String> financialPriorities,
        List<String> expenseCategoriesToProtect,
        List<String> expenseCategoriesUserIsWillingToReduce,
        List<String> expenseCategoriesUserIsWillingToStop,
        List<PaymentMethod> paymentMethodsUserWillConsider,
        Integer maxInstallmentMonths
) {
    public boolean considersMethod(PaymentMethod method) {
        return paymentMethodsUserWillConsider != null && paymentMethodsUserWillConsider.contains(method);
    }

    public boolean isCategoryProtected(String category) {
        return expenseCategoriesToProtect != null && expenseCategoriesToProtect.contains(category);
    }

    public boolean isCategoryWillingToReduce(String category) {
        return expenseCategoriesUserIsWillingToReduce != null && expenseCategoriesUserIsWillingToReduce.contains(category);
    }

    public boolean isCategoryWillingToStop(String category) {
        return expenseCategoriesUserIsWillingToStop != null && expenseCategoriesUserIsWillingToStop.contains(category);
    }
}

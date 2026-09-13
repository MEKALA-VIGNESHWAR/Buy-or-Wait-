package com.orchestrate.buywait.repository.parser;

import com.orchestrate.buywait.model.FinancialProfile;
import com.orchestrate.buywait.model.PaymentMethod;
import com.orchestrate.buywait.util.FormatUtils;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FinancialProfileCsvParser {

    public FinancialProfile parse(CSVRecord record) {
        String userId = FormatUtils.cleanString(record.get("user_id"));
        if (userId == null) {
            throw new IllegalArgumentException("Malformed row at line " + record.getRecordNumber() + ": missing user_id");
        }

        String homeCurrency = FormatUtils.cleanString(record.get("home_currency"));
        BigDecimal currentAvailableBalance = FormatUtils.parseBigDecimal(record.get("current_available_balance"));
        BigDecimal minimumBalanceToKeep = FormatUtils.parseBigDecimal(record.get("minimum_balance_to_keep"));

        List<String> financialPriorities = FormatUtils.parseDelimitedList(record.get("financial_priorities"), "\\|");
        List<String> expenseCategoriesToProtect = FormatUtils.parseDelimitedList(record.get("expense_categories_to_protect"), "\\|");
        List<String> expenseCategoriesUserIsWillingToReduce = FormatUtils.parseDelimitedList(record.get("expense_categories_user_is_willing_to_reduce"), "\\|");
        List<String> expenseCategoriesUserIsWillingToStop = FormatUtils.parseDelimitedList(record.get("expense_categories_user_is_willing_to_stop"), "\\|");
        List<PaymentMethod> paymentMethodsUserWillConsider = FormatUtils.parsePaymentMethodList(record.get("payment_methods_user_will_consider"));
        Integer maxInstallmentMonths = FormatUtils.parseInteger(record.get("max_installment_months"));

        return new FinancialProfile(
                userId,
                homeCurrency,
                currentAvailableBalance,
                minimumBalanceToKeep,
                financialPriorities,
                expenseCategoriesToProtect,
                expenseCategoriesUserIsWillingToReduce,
                expenseCategoriesUserIsWillingToStop,
                paymentMethodsUserWillConsider,
                maxInstallmentMonths
        );
    }
}

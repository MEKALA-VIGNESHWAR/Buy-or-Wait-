package com.orchestrate.buywait.service;

import com.orchestrate.buywait.engine.ForecastSimulationEngine;
import com.orchestrate.buywait.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private static final Logger log = LoggerFactory.getLogger(PaymentPlanServiceImpl.class);

    private final ForecastSimulationEngine forecastEngine;
    private final AmountSafeToPayService amountSafeToPayService;
    private final EarliestFullPaymentService earliestFullPaymentService;
    private final SpendingAdjustmentService spendingAdjustmentService;

    public PaymentPlanServiceImpl(
            ForecastSimulationEngine forecastEngine,
            AmountSafeToPayService amountSafeToPayService,
            EarliestFullPaymentService earliestFullPaymentService,
            @org.springframework.context.annotation.Lazy
            SpendingAdjustmentService spendingAdjustmentService
    ) {
        this.forecastEngine = forecastEngine;
        this.amountSafeToPayService = amountSafeToPayService;
        this.earliestFullPaymentService = earliestFullPaymentService;
        this.spendingAdjustmentService = spendingAdjustmentService;
    }

    @Override
    public List<Plan> generateCandidatePlans(FinancialState financialState) {
        if (financialState == null || financialState.request() == null || financialState.profile() == null) {
            return Collections.emptyList();
        }

        Request request = financialState.request();
        FinancialProfile profile = financialState.profile();
        LocalDate requestDate = request.requestDate();
        LocalDate desiredCompletionDate = request.desiredCompletionDate();
        BigDecimal requestedAmount = request.requestedAmount();

        List<Plan> candidates = new ArrayList<>();

        BigDecimal safeToday = amountSafeToPayService.calculateAmountSafeToPay(financialState);
        Optional<LocalDate> earliestFullDateOpt = earliestFullPaymentService.findEarliestDateForFullPayment(financialState);

        // 1. FULL PAYMENT (Immediate)
        if (profile.considersMethod(PaymentMethod.full_payment)) {
            if (safeToday.compareTo(requestedAmount) >= 0) {
                List<Payment> fullPlan = List.of(new Payment(requestDate, requestedAmount));
                String optId = findMatchingOptionId(financialState.availablePaymentOptions(), PaymentMethod.full_payment, requestedAmount);
                candidates.add(new Plan(PaymentMethod.full_payment, fullPlan, requestedAmount, requestDate, 1, optId, Collections.emptyList()));
            }
        }

        // 2. PARTIAL PAYMENT
        if (request.allowsPartialPayment() && profile.considersMethod(PaymentMethod.partial_payment)) {
            if (safeToday.compareTo(BigDecimal.ZERO) > 0 && safeToday.compareTo(requestedAmount) < 0) {
                if (earliestFullDateOpt.isPresent()) {
                    LocalDate earliestDate = earliestFullDateOpt.get();
                    if (!earliestDate.isAfter(desiredCompletionDate)) {
                        BigDecimal remaining = requestedAmount.subtract(safeToday);
                        List<Payment> partialPayments = List.of(
                                new Payment(requestDate, safeToday),
                                new Payment(earliestDate, remaining)
                        );
                        if (forecastEngine.isSafe(partialPayments, financialState)) {
                            candidates.add(new Plan(PaymentMethod.partial_payment, partialPayments, requestedAmount, earliestDate, 2, null, Collections.emptyList()));
                        }
                    }
                }
            }
        }

        // 3. INSTALLMENTS (from request_payment_options.csv)
        if (profile.considersMethod(PaymentMethod.installments)) {
            for (PaymentOption opt : financialState.availablePaymentOptions()) {
                if (opt.isInstallments()) {
                    Integer maxMonths = profile.maxInstallmentMonths();
                    if (maxMonths != null && opt.numberOfPayments() != null && opt.numberOfPayments() > maxMonths) {
                        log.debug("Option {} rejected: payments {} > max_installment_months {}",
                                opt.paymentOptionId(), opt.numberOfPayments(), maxMonths);
                        continue;
                    }

                    List<Payment> instPayments = generateInstallmentSchedule(opt);
                    if (instPayments.isEmpty()) {
                        continue;
                    }

                    LocalDate completionDate = instPayments.get(instPayments.size() - 1).paymentDate();
                    BigDecimal totalAmount = opt.totalPayableAmount() != null
                            ? opt.totalPayableAmount()
                            : calculateTotal(instPayments);

                    if (forecastEngine.isSafe(instPayments, financialState)) {
                        candidates.add(new Plan(PaymentMethod.installments, instPayments, totalAmount, completionDate, instPayments.size(), opt.paymentOptionId(), Collections.emptyList()));
                    }
                }
            }
        }

        // 4. WAIT
        if (profile.considersMethod(PaymentMethod.full_payment)) {
            if (earliestFullDateOpt.isPresent()) {
                LocalDate earliestDate = earliestFullDateOpt.get();
                if (earliestDate.isAfter(requestDate) && !earliestDate.isAfter(desiredCompletionDate)) {
                    List<Payment> waitPayments = List.of(new Payment(earliestDate, requestedAmount));
                    if (forecastEngine.isSafe(waitPayments, financialState)) {
                        candidates.add(new Plan(PaymentMethod.wait, waitPayments, requestedAmount, earliestDate, 1, null, Collections.emptyList()));
                    }
                }
            }
        }

        // 5. SPENDING-CHANGE-ASSISTED PLANS
        generateSpendingChangeAssistedPlans(financialState, candidates);

        return Collections.unmodifiableList(candidates);
    }

    private void generateSpendingChangeAssistedPlans(FinancialState financialState, List<Plan> candidates) {
        Request request = financialState.request();
        FinancialProfile profile = financialState.profile();
        LocalDate requestDate = request.requestDate();
        BigDecimal requestedAmount = request.requestedAmount();

        // 5a. Spending changes assisting full payment today
        if (profile.considersMethod(PaymentMethod.full_payment)) {
            List<Payment> fullPlan = List.of(new Payment(requestDate, requestedAmount));
            List<List<SpendingChange>> candidateChanges = spendingAdjustmentService.findRankedCandidateSpendingChanges(fullPlan, financialState);
            for (List<SpendingChange> changes : candidateChanges) {
                String optId = findMatchingOptionId(financialState.availablePaymentOptions(), PaymentMethod.full_payment, requestedAmount);
                candidates.add(new Plan(PaymentMethod.full_payment, fullPlan, requestedAmount, requestDate, 1, optId, changes));
            }
        }

        // 5b. Spending changes assisting installment options
        if (profile.considersMethod(PaymentMethod.installments)) {
            for (PaymentOption opt : financialState.availablePaymentOptions()) {
                if (opt.isInstallments()) {
                    Integer maxMonths = profile.maxInstallmentMonths();
                    if (maxMonths != null && opt.numberOfPayments() != null && opt.numberOfPayments() > maxMonths) {
                        continue;
                    }

                    List<Payment> instPayments = generateInstallmentSchedule(opt);
                    if (instPayments.isEmpty()) {
                        continue;
                    }

                    LocalDate completionDate = instPayments.get(instPayments.size() - 1).paymentDate();
                    BigDecimal totalAmount = opt.totalPayableAmount() != null
                            ? opt.totalPayableAmount()
                            : calculateTotal(instPayments);

                    List<List<SpendingChange>> candidateChanges = spendingAdjustmentService.findRankedCandidateSpendingChanges(instPayments, financialState);
                    for (List<SpendingChange> changes : candidateChanges) {
                        candidates.add(new Plan(PaymentMethod.installments, instPayments, totalAmount, completionDate, instPayments.size(), opt.paymentOptionId(), changes));
                    }
                }
            }
        }
    }

    private List<Payment> generateInstallmentSchedule(PaymentOption option) {
        if (option.numberOfPayments() == null || option.paymentAmount() == null || option.firstPaymentDate() == null) {
            return Collections.emptyList();
        }

        int count = option.numberOfPayments();
        BigDecimal paymentAmount = option.paymentAmount();
        LocalDate startDate = option.firstPaymentDate();
        int freqDays = option.paymentFrequencyDays() != null && option.paymentFrequencyDays() > 0
                ? option.paymentFrequencyDays()
                : 30;

        List<Payment> schedule = new ArrayList<>(count);
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (int i = 0; i < count; i++) {
            LocalDate date = startDate.plusDays((long) i * freqDays);
            schedule.add(new Payment(date, paymentAmount));
            runningTotal = runningTotal.add(paymentAmount);
        }

        // Minor cent adjustment on final payment if totalPayableAmount is specified
        if (option.totalPayableAmount() != null && count > 0) {
            BigDecimal diff = option.totalPayableAmount().subtract(runningTotal);
            if (diff.abs().compareTo(new BigDecimal("0.05")) <= 0 && diff.compareTo(BigDecimal.ZERO) != 0) {
                Payment last = schedule.get(count - 1);
                schedule.set(count - 1, new Payment(last.paymentDate(), last.amount().add(diff)));
            }
        }

        return schedule;
    }

    private BigDecimal calculateTotal(List<Payment> payments) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Payment p : payments) {
            sum = sum.add(p.amount());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private String findMatchingOptionId(List<PaymentOption> options, PaymentMethod method, BigDecimal amount) {
        if (options == null) return null;
        for (PaymentOption opt : options) {
            if (opt.paymentMethod() == method) {
                if (opt.totalPayableAmount() != null && opt.totalPayableAmount().compareTo(amount) == 0) {
                    return opt.paymentOptionId();
                }
                if (opt.paymentAmount() != null && opt.paymentAmount().compareTo(amount) == 0) {
                    return opt.paymentOptionId();
                }
            }
        }
        return null;
    }
}

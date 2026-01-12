package com.maple.service.payment;

import com.maple.model.PaymentStatus;
import com.maple.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for payment statistics and analytics.
 * 
 * Provides methods for calculating payment metrics, generating reports,
 * and analyzing payment patterns for business intelligence.
 */
@Service
@Transactional(readOnly = true)
public class PaymentStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentStatisticsService.class);

    private final PaymentRepository paymentRepository;

    @Autowired
    public PaymentStatisticsService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Gets overall payment statistics across all payments.
     * 
     * @return Map of statistics by status
     */
    public Map<PaymentStatus, Long> getOverallStatistics() {
        logger.debug("Calculating overall payment statistics");
        
        List<Object[]> statusCounts = paymentRepository.countByStatus();
        Map<PaymentStatus, Long> statistics = new HashMap<>();
        
        for (Object[] row : statusCounts) {
            PaymentStatus status = (PaymentStatus) row[0];
            Long count = ((Number) row[1]).longValue();
            statistics.put(status, count);
        }
        
        return statistics;
    }

    /**
     * Calculates total payment volume in a period.
     * 
     * @param startDate Start of period
     * @param endDate End of period
     * @return Total amount in cents
     */
    public long calculateTotalVolume(OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Calculating total payment volume from {} to {}", startDate, endDate);
        
        List<com.maple.model.Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        
        return payments.stream()
            .filter(p -> !p.getStatus().isTerminal() || p.getStatus() == PaymentStatus.SETTLED)
            .mapToLong(com.maple.model.Payment::getAmountCents)
            .sum();
    }

    /**
     * Calculates average payment amount.
     * 
     * @param startDate Start of period
     * @param endDate End of period
     * @return Average payment amount in cents
     */
    public double calculateAveragePaymentAmount(OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Calculating average payment amount from {} to {}", startDate, endDate);
        
        List<com.maple.model.Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        
        if (payments.isEmpty()) {
            return 0.0;
        }
        
        long totalAmount = payments.stream()
            .mapToLong(com.maple.model.Payment::getAmountCents)
            .sum();
        
        return (double) totalAmount / payments.size();
    }

    /**
     * Gets high-value payment count above threshold.
     * 
     * @param thresholdCents Threshold in cents
     * @return Count of high-value payments
     */
    public long getHighValuePaymentCount(long thresholdCents) {
        logger.debug("Counting high-value payments above threshold: {}", thresholdCents);
        
        List<com.maple.model.Payment> highValuePayments = 
            paymentRepository.findHighValuePayments(thresholdCents);
        
        return highValuePayments.size();
    }

    /**
     * Calculates payment success rate.
     * 
     * @param startDate Start of period
     * @param endDate End of period
     * @return Success rate as percentage (0-100)
     */
    public double calculateSuccessRate(OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Calculating payment success rate from {} to {}", startDate, endDate);
        
        List<com.maple.model.Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        
        if (payments.isEmpty()) {
            return 0.0;
        }
        
        long successfulCount = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.SETTLED)
            .count();
        
        return (double) successfulCount / payments.size() * 100.0;
    }

    /**
     * Calculates approval rate.
     * 
     * @param startDate Start of period
     * @param endDate End of period
     * @return Approval rate as percentage (0-100)
     */
    public double calculateApprovalRate(OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Calculating approval rate from {} to {}", startDate, endDate);
        
        List<com.maple.model.Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        
        if (payments.isEmpty()) {
            return 0.0;
        }
        
        long requiringApproval = payments.stream()
            .filter(com.maple.model.Payment::getApprovalRequired)
            .count();
        
        if (requiringApproval == 0) {
            return 100.0; // All payments auto-approved
        }
        
        long approvedCount = payments.stream()
            .filter(p -> p.getApprovalRequired() && 
                        (p.getStatus() == PaymentStatus.APPROVED || 
                         p.getStatus() == PaymentStatus.SUBMITTED || 
                         p.getStatus() == PaymentStatus.SETTLED))
            .count();
        
        return (double) approvedCount / requiringApproval * 100.0;
    }

    /**
     * Gets payment counts by status in a period.
     * 
     * @param startDate Start of period
     * @param endDate End of period
     * @return Map of status to count
     */
    public Map<PaymentStatus, Long> getStatusBreakdown(OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Getting status breakdown from {} to {}", startDate, endDate);
        
        List<com.maple.model.Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        
        Map<PaymentStatus, Long> breakdown = new HashMap<>();
        for (com.maple.model.Payment payment : payments) {
            breakdown.merge(payment.getStatus(), 1L, Long::sum);
        }
        
        return breakdown;
    }

    /**
     * Gets pending approvals count.
     * 
     * @return Number of payments pending approval
     */
    public long getPendingApprovalsCount() {
        logger.debug("Getting pending approvals count");
        
        List<com.maple.model.Payment> pending = 
            paymentRepository.findByStatus(PaymentStatus.PENDING_APPROVAL);
        
        return pending.size();
    }

    /**
     * Gets payments requiring reconciliation.
     * 
     * @param hoursSinceSubmission Hours since submission to consider for reconciliation
     * @return List of payments requiring reconciliation
     */
    public List<com.maple.model.Payment> getPaymentsRequiringReconciliation(int hoursSinceSubmission) {
        logger.debug("Getting payments requiring reconciliation ({} hours since submission)", hoursSinceSubmission);
        
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(hoursSinceSubmission);
        return paymentRepository.findPaymentsRequiringReconciliation(cutoffTime);
    }
}


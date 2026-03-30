package com.maple.service.payment;

import com.maple.dto.PaymentResponseDto;
import com.maple.event.PaymentEvents;
import com.maple.model.Approval;
import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import com.maple.repository.ApprovalRepository;
import com.maple.repository.PaymentBatchRepository;
import com.maple.repository.PaymentRepository;
import com.maple.service.approval.HolidayCalendarService;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for payment lifecycle management operations.
 * 
 * Handles payment approval, rejection, cancellation, and retrieval
 * with proper state transitions, validation, audit logging, and event publishing.
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentBatchRepository paymentBatchRepository;
    private final ApprovalRepository approvalRepository;
    private final AuditService auditService;
    private final HolidayCalendarService holidayCalendarService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                         PaymentBatchRepository paymentBatchRepository,
                         ApprovalRepository approvalRepository,
                         AuditService auditService,
                         HolidayCalendarService holidayCalendarService,
                         KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.paymentBatchRepository = paymentBatchRepository;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
        this.holidayCalendarService = holidayCalendarService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Retrieves a payment by ID with pessimistic locking for updates.
     * 
     * @param paymentId The payment ID
     * @return Payment entity
     * @throws PaymentNotFoundException if payment does not exist
     */
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * Retrieves a payment by ID without locking (for read operations).
     * 
     * @param paymentId The payment ID
     * @return Payment entity
     * @throws PaymentNotFoundException if payment does not exist
     */
    public Payment findPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * Approves a payment that requires approval.
     * 
     * @param paymentId The payment ID
     * @param approverId The ID of the user approving the payment
     * @param approvalNote Optional note for the approval
     * @param twoFactorVerified Whether 2FA was verified (optional, defaults to false)
     * @return The approved payment
     * @throws PaymentNotFoundException if payment does not exist
     * @throws PaymentStateException if payment cannot be approved in its current state
     */
    public Payment approvePayment(UUID paymentId, UUID approverId, String approvalNote, Boolean twoFactorVerified) {
        logger.info("Processing approval for payment: {} by approver: {}", paymentId, approverId);

        Payment payment = getPaymentById(paymentId);

        // Validate payment state
        if (!payment.getApprovalRequired()) {
            throw new PaymentStateException("Payment does not require approval");
        }

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new PaymentStateException(
                "Payment is not in PENDING_APPROVAL status. Current status: " + payment.getStatus());
        }

        holidayCalendarService.assertApprovalAllowed(payment.getHolidayCalendarId(), OffsetDateTime.now());

        if (approvalRepository.existsByPaymentIdAndApproverId(paymentId, approverId)) {
            throw new PaymentStateException("This approver has already recorded an approval for this payment");
        }

        // Store previous status for audit
        PaymentStatus previousStatus = payment.getStatus();

        boolean finalized;
        try {
            finalized = payment.recordApproval(approverId);
        } catch (IllegalStateException e) {
            throw new PaymentStateException(e.getMessage());
        }

        Approval approval = new Approval(paymentId, approverId, Approval.ApprovalAction.APPROVED, approvalNote);
        if (twoFactorVerified != null) {
            approval.setTwoFactorVerified(twoFactorVerified);
        }
        approvalRepository.save(approval);

        Payment savedPayment = paymentRepository.save(payment);

        if (finalized) {
            logger.info("Payment {} fully approved by {}", paymentId, approverId);
            auditService.auditApprovalEvent(
                approverId.toString(),
                "PAYMENT_APPROVED",
                paymentId,
                "APPROVE"
            );
            auditService.auditPaymentStatusChange(
                approverId.toString(),
                paymentId,
                previousStatus.toString(),
                savedPayment.getStatus().toString()
            );
            publishApprovalEvent(savedPayment, approverId, approvalNote,
                    twoFactorVerified != null ? twoFactorVerified : false);
        } else {
            logger.info("Payment {} first approval recorded by {} (dual control)", paymentId, approverId);
            auditService.auditApprovalEvent(
                approverId.toString(),
                    "PAYMENT_DUAL_APPROVAL_STEP",
                    paymentId,
                    "APPROVE_FIRST"
            );
            publishDualApprovalStepEvent(savedPayment, approverId, approvalNote);
        }

        return savedPayment;
    }

    /**
     * Rejects a payment that requires approval.
     * 
     * @param paymentId The payment ID
     * @param rejectorId The ID of the user rejecting the payment
     * @param rejectionReason Reason for rejection
     * @return The rejected payment
     * @throws PaymentNotFoundException if payment does not exist
     * @throws PaymentStateException if payment cannot be rejected in its current state
     */
    public Payment rejectPayment(UUID paymentId, UUID rejectorId, String rejectionReason) {
        logger.info("Processing rejection for payment: {} by rejector: {}", paymentId, rejectorId);

        Payment payment = getPaymentById(paymentId);

        // Validate payment state
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new PaymentStateException(
                "Payment is not in PENDING_APPROVAL status. Current status: " + payment.getStatus());
        }

        // Store previous status for audit
        PaymentStatus previousStatus = payment.getStatus();

        // Reject the payment
        payment.reject();
        Payment savedPayment = paymentRepository.save(payment);

        logger.info("Payment {} rejected by {} with reason: {}", paymentId, rejectorId, rejectionReason);

        // Audit the rejection
        auditService.auditApprovalEvent(
            rejectorId.toString(),
            "PAYMENT_REJECTED",
            paymentId,
            "REJECT"
        );

        auditService.auditPaymentStatusChange(
            rejectorId.toString(),
            paymentId,
            previousStatus.toString(),
            savedPayment.getStatus().toString()
        );

        // Publish rejection event
        publishRejectionEvent(savedPayment, rejectorId, rejectionReason);

        return savedPayment;
    }

    /**
     * Cancels a payment if it's in a cancellable state.
     * 
     * @param paymentId The payment ID
     * @param cancellerId The ID of the user cancelling the payment
     * @param cancellationReason Optional reason for cancellation
     * @return The cancelled payment
     * @throws PaymentNotFoundException if payment does not exist
     * @throws PaymentStateException if payment cannot be cancelled in its current state
     */
    public Payment cancelPayment(UUID paymentId, UUID cancellerId, String cancellationReason) {
        logger.info("Processing cancellation for payment: {} by user: {}", paymentId, cancellerId);

        Payment payment = getPaymentById(paymentId);

        // Validate payment state
        if (!payment.getStatus().canBeCancelled()) {
            throw new PaymentStateException(
                "Payment cannot be cancelled in current status: " + payment.getStatus());
        }

        // Store previous status for audit
        PaymentStatus previousStatus = payment.getStatus();

        // Cancel the payment
        payment.cancel();
        Payment savedPayment = paymentRepository.save(payment);

        logger.info("Payment {} cancelled by {}", paymentId, cancellerId);

        // Audit the cancellation
        auditService.auditPaymentEvent(
            cancellerId.toString(),
            "PAYMENT_CANCELLED",
            paymentId
        );

        auditService.auditPaymentStatusChange(
            cancellerId.toString(),
            paymentId,
            previousStatus.toString(),
            savedPayment.getStatus().toString()
        );

        // Publish cancellation event
        publishCancellationEvent(savedPayment, cancellerId, cancellationReason);

        return savedPayment;
    }

    /**
     * Converts Payment entity to response DTO.
     */
    public PaymentResponseDto convertToResponseDto(Payment payment, String operationId) {
        PaymentResponseDto.PaymentResponseDtoBuilder b =
                PaymentResponseDto.builder()
                        .id(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .amountCents(payment.getAmountCents())
                        .formattedAmount(formatAmount(payment.getAmountCents(), payment.getCurrency()))
                        .currency(payment.getCurrency())
                        .debtorAccount(payment.getDebtorAccount())
                        .creditorAccount(payment.getCreditorAccount())
                        .creditorName(payment.getCreditorName())
                        .paymentPurpose(payment.getPaymentPurpose())
                        .status(payment.getStatus())
                        .initiatedBy(payment.getInitiatedBy())
                        .approvedBy(payment.getApprovedBy())
                        .approvalRequired(payment.getApprovalRequired())
                        .requiredApprovers(payment.getRequiredApprovers())
                        .firstApprovalBy(payment.getFirstApprovalBy())
                        .firstApprovalAt(payment.getFirstApprovalAt())
                        .escalationDueAt(payment.getEscalationDueAt())
                        .escalationLevel(payment.getEscalationLevel())
                        .submittedAt(payment.getSubmittedAt())
                        .settledAt(payment.getSettledAt())
                        .iso20022Filename(payment.getIso20022Filename())
                        .batchId(payment.getBatchId())
                        .createdAt(payment.getCreatedAt())
                        .updatedAt(payment.getUpdatedAt())
                        .operationId(operationId);
        if (payment.getBatchId() != null) {
            paymentBatchRepository
                    .findByBatchReference(payment.getBatchId())
                    .ifPresent(
                            pb -> {
                                b.batchDeliveryStatus(
                                        pb.getSftpDeliveryStatus() != null
                                                ? pb.getSftpDeliveryStatus().name()
                                                : null);
                                b.batchReceiptReceivedAt(pb.getReceiptReceivedAt());
                            });
        }
        return b.build();
    }

    /**
     * Publishes payment approval event to Kafka.
     */
    private void publishApprovalEvent(Payment payment, UUID approvedBy, String approvalNote, boolean twoFactorVerified) {
        try {
            PaymentEvents.PaymentApproved event = new PaymentEvents.PaymentApproved(
                    payment.getId(),
                    payment.getPaymentReference(),
                    approvedBy,
                    approvalNote,
                    twoFactorVerified
            );

            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published payment approval event for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish payment approval event", e);
        }
    }

    private void publishDualApprovalStepEvent(Payment payment, UUID stepApprover, String approvalNote) {
        try {
            PaymentEvents.PaymentDualApprovalStep event = new PaymentEvents.PaymentDualApprovalStep(
                    payment.getId(),
                    payment.getPaymentReference(),
                    stepApprover,
                    payment.getRequiredApprovers(),
                    approvalNote
            );
            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published dual approval step event for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish dual approval step event", e);
        }
    }

    /**
     * Publishes payment rejection event to Kafka.
     */
    private void publishRejectionEvent(Payment payment, UUID rejectedBy, String rejectionReason) {
        try {
            PaymentEvents.PaymentRejected event = new PaymentEvents.PaymentRejected(
                    payment.getId(),
                    payment.getPaymentReference(),
                    rejectedBy,
                    rejectionReason
            );

            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published payment rejection event for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish payment rejection event", e);
        }
    }

    /**
     * Publishes payment cancellation event to Kafka.
     */
    private void publishCancellationEvent(Payment payment, UUID cancelledBy, String cancellationReason) {
        try {
            PaymentEvents.PaymentCancelled event = new PaymentEvents.PaymentCancelled(
                    payment.getId(),
                    payment.getPaymentReference(),
                    cancelledBy,
                    cancellationReason
            );

            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published payment cancellation event for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish payment cancellation event", e);
        }
    }

    /**
     * Batch approves multiple payments.
     * 
     * @param paymentIds List of payment IDs to approve
     * @param approverId The ID of the user approving the payments
     * @param approvalNote Optional note for the approvals
     * @param twoFactorVerified Whether 2FA was verified
     * @return Map with payment IDs as keys and results (success/error) as values
     */
    public Map<UUID, BatchOperationResult> batchApprovePayments(
            List<UUID> paymentIds, UUID approverId, String approvalNote, Boolean twoFactorVerified) {
        logger.info("Processing batch approval for {} payments by approver: {}", paymentIds.size(), approverId);
        
        return paymentIds.stream().collect(Collectors.toMap(
            paymentId -> paymentId,
            paymentId -> {
                try {
                    approvePayment(paymentId, approverId, approvalNote, twoFactorVerified);
                    return new BatchOperationResult(true, "Payment approved successfully", null);
                } catch (PaymentNotFoundException e) {
                    logger.warn("Payment not found in batch approval: {}", paymentId);
                    return new BatchOperationResult(false, "Payment not found", e.getMessage());
                } catch (PaymentStateException e) {
                    logger.warn("Invalid state for batch approval: {} - {}", paymentId, e.getMessage());
                    return new BatchOperationResult(false, "Invalid payment state", e.getMessage());
                } catch (Exception e) {
                    logger.error("Error in batch approval for payment: {}", paymentId, e);
                    return new BatchOperationResult(false, "Approval failed", e.getMessage());
                }
            }
        ));
    }

    /**
     * Batch rejects multiple payments.
     * 
     * @param paymentIds List of payment IDs to reject
     * @param rejectorId The ID of the user rejecting the payments
     * @param rejectionReason Reason for rejection
     * @return Map with payment IDs as keys and results (success/error) as values
     */
    public Map<UUID, BatchOperationResult> batchRejectPayments(
            List<UUID> paymentIds, UUID rejectorId, String rejectionReason) {
        logger.info("Processing batch rejection for {} payments by rejector: {}", paymentIds.size(), rejectorId);
        
        return paymentIds.stream().collect(Collectors.toMap(
            paymentId -> paymentId,
            paymentId -> {
                try {
                    rejectPayment(paymentId, rejectorId, rejectionReason);
                    return new BatchOperationResult(true, "Payment rejected successfully", null);
                } catch (PaymentNotFoundException e) {
                    logger.warn("Payment not found in batch rejection: {}", paymentId);
                    return new BatchOperationResult(false, "Payment not found", e.getMessage());
                } catch (PaymentStateException e) {
                    logger.warn("Invalid state for batch rejection: {} - {}", paymentId, e.getMessage());
                    return new BatchOperationResult(false, "Invalid payment state", e.getMessage());
                } catch (Exception e) {
                    logger.error("Error in batch rejection for payment: {}", paymentId, e);
                    return new BatchOperationResult(false, "Rejection failed", e.getMessage());
                }
            }
        ));
    }

    /**
     * Retrieves payments pending approval with pagination.
     * 
     * @param pageable Pagination parameters
     * @return Page of payments pending approval
     */
    public Page<Payment> getPendingApprovals(Pageable pageable) {
        logger.debug("Retrieving pending approvals with pagination");
        return paymentRepository.findPendingApprovals(pageable);
    }

    /**
     * Retrieves payments initiated by a specific user with pagination.
     * Used for user-to-payment mapping queries.
     *
     * @param userId User ID (initiator)
     * @param pageable Pagination parameters
     * @return Page of payments initiated by the user
     */
    public Page<Payment> getPaymentsByUser(UUID userId, Pageable pageable) {
        logger.debug("Retrieving payments for user: {}", userId);
        return paymentRepository.findByInitiatedBy(userId, pageable);
    }

    /**
     * Retrieves payment history/audit trail for a payment.
     * This method fetches all status changes and related events.
     * 
     * @param paymentId The payment ID
     * @return List of payment status changes with timestamps
     */
    public List<PaymentHistoryEntry> getPaymentHistory(UUID paymentId) {
        logger.debug("Retrieving payment history for: {}", paymentId);
        
        Payment payment = findPaymentById(paymentId);
        List<PaymentHistoryEntry> history = new ArrayList<>();
        
        // Add creation entry
        history.add(new PaymentHistoryEntry(
            payment.getCreatedAt(),
            "CREATED",
            "Payment created",
            payment.getInitiatedBy(),
            null
        ));
        
        // Add approval required status if applicable
        if (payment.getApprovalRequired() && payment.getStatus() == PaymentStatus.PENDING_APPROVAL) {
            history.add(new PaymentHistoryEntry(
                payment.getUpdatedAt(),
                "PENDING_APPROVAL",
                "Awaiting approval",
                null,
                null
            ));
        }
        
        // Add approval entry if approved
        if (payment.getApprovedBy() != null && payment.getStatus() == PaymentStatus.APPROVED) {
            history.add(new PaymentHistoryEntry(
                payment.getUpdatedAt(),
                "APPROVED",
                "Payment approved",
                payment.getApprovedBy(),
                null
            ));
        }
        
        // Add rejection entry if rejected
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            history.add(new PaymentHistoryEntry(
                payment.getUpdatedAt(),
                "REJECTED",
                "Payment rejected",
                null,
                null
            ));
        }
        
        // Add submission entry if submitted
        if (payment.getSubmittedAt() != null) {
            history.add(new PaymentHistoryEntry(
                payment.getSubmittedAt(),
                "SUBMITTED",
                "Submitted to clearing network",
                null,
                payment.getBatchId()
            ));
        }
        
        // Add settlement entry if settled
        if (payment.getSettledAt() != null) {
            history.add(new PaymentHistoryEntry(
                payment.getSettledAt(),
                "SETTLED",
                "Payment settled",
                null,
                null
            ));
        }
        
        // Add cancellation entry if cancelled
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            history.add(new PaymentHistoryEntry(
                payment.getUpdatedAt(),
                "CANCELLED",
                "Payment cancelled",
                null,
                null
            ));
        }
        
        // Add failure entry if failed
        if (payment.getStatus() == PaymentStatus.FAILED) {
            history.add(new PaymentHistoryEntry(
                payment.getUpdatedAt(),
                "FAILED",
                "Payment processing failed",
                null,
                null
            ));
        }
        
        return history;
    }

    /**
     * Calculates payment statistics for a user within a time period.
     * 
     * @param userId User ID
     * @param startDate Start of period
     * @param endDate End of period
     * @return Payment statistics
     */
    public PaymentStatistics calculateUserStatistics(UUID userId, OffsetDateTime startDate, OffsetDateTime endDate) {
        logger.debug("Calculating statistics for user: {} from {} to {}", userId, startDate, endDate);
        
        Long totalAmount = paymentRepository.calculateTotalAmountForUserInPeriod(userId, startDate, endDate);
        
        List<Payment> userPayments = paymentRepository.findByInitiatedBy(
            userId, 
            org.springframework.data.domain.Pageable.unpaged()
        ).getContent();
        
        long countByStatus = userPayments.stream()
            .filter(p -> p.getCreatedAt().isAfter(startDate) && p.getCreatedAt().isBefore(endDate))
            .count();
        
        long approvedCount = userPayments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.APPROVED || p.getStatus() == PaymentStatus.SETTLED || p.getStatus() == PaymentStatus.SUBMITTED)
            .filter(p -> p.getCreatedAt().isAfter(startDate) && p.getCreatedAt().isBefore(endDate))
            .count();
        
        long pendingCount = userPayments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.PENDING_APPROVAL)
            .filter(p -> p.getCreatedAt().isAfter(startDate) && p.getCreatedAt().isBefore(endDate))
            .count();
        
        long rejectedCount = userPayments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.REJECTED)
            .filter(p -> p.getCreatedAt().isAfter(startDate) && p.getCreatedAt().isBefore(endDate))
            .count();
        
        return new PaymentStatistics(
            countByStatus,
            approvedCount,
            pendingCount,
            rejectedCount,
            totalAmount != null ? totalAmount : 0L,
            startDate,
            endDate
        );
    }

    /**
     * Retries a failed payment by resetting its status to APPROVED if it was previously approved,
     * or to CREATED if it didn't require approval.
     * 
     * @param paymentId The payment ID to retry
     * @param retryBy The ID of the user initiating the retry
     * @param retryReason Optional reason for the retry
     * @return The payment with updated status
     * @throws PaymentNotFoundException if payment does not exist
     * @throws PaymentStateException if payment cannot be retried
     */
    public Payment retryFailedPayment(UUID paymentId, UUID retryBy, String retryReason) {
        logger.info("Retrying failed payment: {} by user: {}", paymentId, retryBy);

        Payment payment = getPaymentById(paymentId);

        // Validate payment state
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new PaymentStateException(
                "Payment is not in FAILED status. Current status: " + payment.getStatus());
        }

        // Store previous status for audit
        PaymentStatus previousStatus = payment.getStatus();

        // Determine new status based on previous workflow
        PaymentStatus newStatus;
        if (payment.getApprovalRequired() && payment.getApprovedBy() != null) {
            // Was approved before, set back to approved
            newStatus = PaymentStatus.APPROVED;
        } else if (!payment.getApprovalRequired()) {
            // Didn't require approval, set back to created
            newStatus = PaymentStatus.CREATED;
        } else {
            // This shouldn't happen but handle it
            throw new PaymentStateException(
                "Cannot determine appropriate retry status for payment: " + paymentId);
        }

        payment.setStatus(newStatus);
        Payment savedPayment = paymentRepository.save(payment);

        logger.info("Payment {} retried successfully, status changed from {} to {}", 
                   paymentId, previousStatus, newStatus);

        // Audit the retry
        auditService.auditPaymentEvent(
            retryBy.toString(),
            "PAYMENT_RETRY",
            paymentId
        );

        auditService.auditPaymentStatusChange(
            retryBy.toString(),
            paymentId,
            previousStatus.toString(),
            savedPayment.getStatus().toString()
        );

        // Publish retry event
        publishRetryEvent(savedPayment, retryBy, retryReason, previousStatus);

        return savedPayment;
    }

    /**
     * Searches payments with multiple filter criteria.
     * 
     * @param paymentReference Optional payment reference filter (partial match)
     * @param debtorAccount Optional debtor account filter (partial match)
     * @param creditorAccount Optional creditor account filter (partial match)
     * @param status Optional status filter
     * @param initiatedBy Optional initiator user ID filter
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param minAmountCents Optional minimum amount filter
     * @param maxAmountCents Optional maximum amount filter
     * @param currency Optional currency filter
     * @param pageable Pagination parameters
     * @return Page of matching payments
     */
    public Page<Payment> searchPayments(String paymentReference, String debtorAccount, 
                                       String creditorAccount, PaymentStatus status,
                                       UUID initiatedBy, OffsetDateTime startDate, 
                                       OffsetDateTime endDate, Long minAmountCents,
                                       Long maxAmountCents, String currency,
                                       Pageable pageable) {
        logger.debug("Searching payments with filters");

        // First get results from repository search
        Page<Payment> payments = paymentRepository.searchPayments(
            paymentReference, debtorAccount, creditorAccount, status, 
            initiatedBy, startDate, endDate, pageable
        );

        // Apply additional filters in memory if needed
        if (minAmountCents != null || maxAmountCents != null || currency != null) {
            List<Payment> filtered = payments.getContent().stream()
                .filter(p -> minAmountCents == null || p.getAmountCents() >= minAmountCents)
                .filter(p -> maxAmountCents == null || p.getAmountCents() <= maxAmountCents)
                .filter(p -> currency == null || currency.equals(p.getCurrency()))
                .collect(Collectors.toList());

            // Create a new page with filtered content
            // Note: This is a simplified approach - for production, filters should be in DB query
            return new org.springframework.data.domain.PageImpl<>(
                filtered, pageable, filtered.size());
        }

        return payments;
    }

    /**
     * Gets payments that failed and may need retry.
     * 
     * @param hoursSinceFailure Hours since failure to consider
     * @return List of failed payments
     */
    public List<Payment> getRetryableFailedPayments(int hoursSinceFailure) {
        logger.debug("Getting retryable failed payments (failed within {} hours)", hoursSinceFailure);
        
        OffsetDateTime since = OffsetDateTime.now().minusHours(hoursSinceFailure);
        return paymentRepository.findRecentFailures(since);
    }

    /**
     * Validates if a payment status transition is allowed.
     * 
     * @param currentStatus Current payment status
     * @param targetStatus Target payment status
     * @return true if transition is allowed
     */
    public boolean isValidStatusTransition(PaymentStatus currentStatus, PaymentStatus targetStatus) {
        // Terminal states cannot transition
        if (currentStatus.isTerminal()) {
            return false;
        }

        // Same status transition is always valid (idempotent)
        if (currentStatus == targetStatus) {
            return true;
        }

        // Define valid transitions
        return switch (currentStatus) {
            case CREATED -> targetStatus == PaymentStatus.PENDING_APPROVAL || 
                           targetStatus == PaymentStatus.APPROVED ||
                           targetStatus == PaymentStatus.CANCELLED;
            case PENDING_APPROVAL -> targetStatus == PaymentStatus.APPROVED ||
                                    targetStatus == PaymentStatus.REJECTED ||
                                    targetStatus == PaymentStatus.CANCELLED;
            case APPROVED -> targetStatus == PaymentStatus.SUBMITTED ||
                            targetStatus == PaymentStatus.CANCELLED ||
                            targetStatus == PaymentStatus.FAILED; // For retry scenarios
            case SUBMITTED -> targetStatus == PaymentStatus.SETTLED ||
                             targetStatus == PaymentStatus.FAILED;
            case FAILED -> targetStatus == PaymentStatus.APPROVED ||
                          targetStatus == PaymentStatus.CREATED; // For retry
            default -> false;
        };
    }

    /**
     * Gets payments requiring attention (pending approval for too long, failed payments, etc.).
     * 
     * @param pendingApprovalHoursThreshold Hours threshold for pending approvals
     * @return List of payments requiring attention
     */
    public List<Payment> getPaymentsRequiringAttention(int pendingApprovalHoursThreshold) {
        logger.debug("Getting payments requiring attention");

        List<Payment> attentionRequired = new ArrayList<>();
        
        // Find payments pending approval for too long
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(pendingApprovalHoursThreshold);
        List<Payment> allPending = paymentRepository.findByStatus(PaymentStatus.PENDING_APPROVAL);
        attentionRequired.addAll(
            allPending.stream()
                .filter(p -> p.getCreatedAt().isBefore(threshold))
                .collect(Collectors.toList())
        );
        
        // Find recent failures
        attentionRequired.addAll(getRetryableFailedPayments(24));
        
        return attentionRequired;
    }

    /**
     * Publishes payment retry event to Kafka.
     */
    private void publishRetryEvent(Payment payment, UUID retriedBy, String retryReason, PaymentStatus previousStatus) {
        try {
            PaymentEvents.PaymentStatusChanged event = new PaymentEvents.PaymentStatusChanged(
                    payment.getId(),
                    payment.getPaymentReference(),
                    previousStatus,
                    payment.getStatus(),
                    retriedBy,
                    retryReason != null ? retryReason : "Payment retried after failure"
            );

            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published payment retry event for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish payment retry event", e);
        }
    }

    /**
     * Formats amount for display.
     */
    private String formatAmount(Long amountCents, String currency) {
        if (amountCents == null) return "0.00";
        return String.format("%.2f %s", amountCents / 100.0, currency);
    }

    /**
     * Result class for batch operations.
     */
    public static class BatchOperationResult {
        private final boolean success;
        private final String message;
        private final String errorDetails;

        public BatchOperationResult(boolean success, String message, String errorDetails) {
            this.success = success;
            this.message = message;
            this.errorDetails = errorDetails;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getErrorDetails() { return errorDetails; }
    }

    /**
     * Payment history entry representing a status change.
     */
    public static class PaymentHistoryEntry {
        private final OffsetDateTime timestamp;
        private final String status;
        private final String description;
        private final UUID actorId;
        private final String additionalInfo;

        public PaymentHistoryEntry(OffsetDateTime timestamp, String status, String description, 
                                  UUID actorId, String additionalInfo) {
            this.timestamp = timestamp;
            this.status = status;
            this.description = description;
            this.actorId = actorId;
            this.additionalInfo = additionalInfo;
        }

        public OffsetDateTime getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public String getDescription() { return description; }
        public UUID getActorId() { return actorId; }
        public String getAdditionalInfo() { return additionalInfo; }
    }

    /**
     * Payment statistics for a user within a time period.
     */
    public static class PaymentStatistics {
        private final long totalCount;
        private final long approvedCount;
        private final long pendingCount;
        private final long rejectedCount;
        private final long totalAmountCents;
        private final OffsetDateTime periodStart;
        private final OffsetDateTime periodEnd;

        public PaymentStatistics(long totalCount, long approvedCount, long pendingCount, 
                               long rejectedCount, long totalAmountCents,
                               OffsetDateTime periodStart, OffsetDateTime periodEnd) {
            this.totalCount = totalCount;
            this.approvedCount = approvedCount;
            this.pendingCount = pendingCount;
            this.rejectedCount = rejectedCount;
            this.totalAmountCents = totalAmountCents;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }

        public long getTotalCount() { return totalCount; }
        public long getApprovedCount() { return approvedCount; }
        public long getPendingCount() { return pendingCount; }
        public long getRejectedCount() { return rejectedCount; }
        public long getTotalAmountCents() { return totalAmountCents; }
        public OffsetDateTime getPeriodStart() { return periodStart; }
        public OffsetDateTime getPeriodEnd() { return periodEnd; }
        
        public double getApprovalRate() {
            return totalCount > 0 ? (double) approvedCount / totalCount * 100.0 : 0.0;
        }
        
        public double getRejectionRate() {
            return totalCount > 0 ? (double) rejectedCount / totalCount * 100.0 : 0.0;
        }
    }

    /**
     * Exception thrown when a payment is not found.
     */
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when a payment operation is invalid due to payment state.
     */
    public static class PaymentStateException extends RuntimeException {
        public PaymentStateException(String message) {
            super(message);
        }
    }
}


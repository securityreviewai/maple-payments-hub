package com.maple.service.payment;

import com.maple.dto.PaymentResponseDto;
import com.maple.event.PaymentEvents;
import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import com.maple.repository.PaymentRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                         AuditService auditService,
                         KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
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

        // Store previous status for audit
        PaymentStatus previousStatus = payment.getStatus();

        // Approve the payment
        payment.approve(approverId);
        Payment savedPayment = paymentRepository.save(payment);

        logger.info("Payment {} approved successfully by {}", paymentId, approverId);

        // Audit the approval
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

        // Publish approval event
        publishApprovalEvent(savedPayment, approverId, approvalNote, 
                           twoFactorVerified != null ? twoFactorVerified : false);

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
        return PaymentResponseDto.builder()
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
                .submittedAt(payment.getSubmittedAt())
                .settledAt(payment.getSettledAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .operationId(operationId)
                .build();
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
     * Formats amount for display.
     */
    private String formatAmount(Long amountCents, String currency) {
        if (amountCents == null) return "0.00";
        return String.format("%.2f %s", amountCents / 100.0, currency);
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


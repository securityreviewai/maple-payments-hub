package com.maple.service.payment;

import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for payment validation and business rule checks.
 * 
 * Provides reusable validation logic for payment operations.
 */
@Component
public class PaymentValidationHelper {

    /**
     * Validates that a payment can be approved.
     * 
     * @param payment The payment to validate
     * @param approverId The ID of the approver
     * @throws PaymentService.PaymentStateException if payment cannot be approved
     */
    public void validateApprovalEligibility(Payment payment, UUID approverId) {
        if (!payment.getApprovalRequired()) {
            throw new PaymentService.PaymentStateException(
                "Payment does not require approval");
        }

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new PaymentService.PaymentStateException(
                String.format("Payment is not in PENDING_APPROVAL status. Current status: %s", 
                             payment.getStatus()));
        }

        // Prevent self-approval if payment was initiated by the same user
        if (payment.getInitiatedBy().equals(approverId)) {
            throw new PaymentService.PaymentStateException(
                "Users cannot approve payments they initiated");
        }
    }

    /**
     * Validates that a payment can be rejected.
     * 
     * @param payment The payment to validate
     * @throws PaymentService.PaymentStateException if payment cannot be rejected
     */
    public void validateRejectionEligibility(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new PaymentService.PaymentStateException(
                String.format("Payment is not in PENDING_APPROVAL status. Current status: %s", 
                             payment.getStatus()));
        }
    }

    /**
     * Validates that a payment can be cancelled.
     * 
     * @param payment The payment to validate
     * @throws PaymentService.PaymentStateException if payment cannot be cancelled
     */
    public void validateCancellationEligibility(Payment payment) {
        if (!payment.getStatus().canBeCancelled()) {
            throw new PaymentService.PaymentStateException(
                String.format("Payment cannot be cancelled in current status: %s", 
                             payment.getStatus()));
        }
    }

    /**
     * Checks if a payment requires approval based on amount and business rules.
     * 
     * @param amountCents Payment amount in cents
     * @param approvalThresholdCents Threshold above which approval is required
     * @return true if approval is required
     */
    public boolean requiresApproval(long amountCents, long approvalThresholdCents) {
        return amountCents > approvalThresholdCents;
    }

    /**
     * Validates rejection reason is provided and not empty.
     * 
     * @param rejectionReason The rejection reason
     * @throws IllegalArgumentException if rejection reason is invalid
     */
    public void validateRejectionReason(String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required and cannot be empty");
        }
        
        if (rejectionReason.trim().length() < 10) {
            throw new IllegalArgumentException("Rejection reason must be at least 10 characters long");
        }
    }

    /**
     * Checks if a payment is in a terminal state (cannot be modified).
     * 
     * @param payment The payment to check
     * @return true if payment is in terminal state
     */
    public boolean isTerminalState(Payment payment) {
        return payment.getStatus().isTerminal();
    }

    /**
     * Checks if a payment can be submitted to clearing.
     * 
     * @param payment The payment to check
     * @return true if payment can be submitted
     */
    public boolean canBeSubmittedToClearing(Payment payment) {
        return (payment.getStatus() == PaymentStatus.APPROVED) ||
               (payment.getStatus() == PaymentStatus.CREATED && !payment.getApprovalRequired());
    }
}


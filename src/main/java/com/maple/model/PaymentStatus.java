package com.maple.model;

/**
 * Enumeration representing the lifecycle status of a payment.
 * 
 * Payment flows through these states:
 * CREATED -> PENDING_APPROVAL -> APPROVED -> SUBMITTED -> SETTLED
 *         -> REJECTED (terminal)
 *         -> CANCELLED (can happen at most stages)
 *         -> FAILED (can happen during SUBMITTED)
 */
public enum PaymentStatus {
    /**
     * Payment has been created but not yet validated or submitted for approval
     */
    CREATED,
    
    /**
     * Payment requires manual approval and is waiting for approver action
     */
    PENDING_APPROVAL,
    
    /**
     * Payment has been approved and is ready for batch processing
     */
    APPROVED,
    
    /**
     * Payment has been rejected by an approver
     */
    REJECTED,
    
    /**
     * Payment has been submitted to the clearing network
     */
    SUBMITTED,
    
    /**
     * Payment has been successfully settled by the bank
     */
    SETTLED,
    
    /**
     * Payment failed during processing (after submission)
     */
    FAILED,
    
    /**
     * Payment was cancelled before submission
     */
    CANCELLED;
    
    /**
     * @return true if this status represents a terminal state (no further transitions)
     */
    public boolean isTerminal() {
        return this == SETTLED || this == FAILED || this == REJECTED || this == CANCELLED;
    }
    
    /**
     * @return true if payment can be cancelled from this status
     */
    public boolean canBeCancelled() {
        return this == CREATED || this == PENDING_APPROVAL || this == APPROVED;
    }
    
    /**
     * @return true if payment requires approval workflow
     */
    public boolean requiresApproval() {
        return this == PENDING_APPROVAL;
    }
}

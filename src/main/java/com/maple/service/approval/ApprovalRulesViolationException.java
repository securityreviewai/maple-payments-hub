package com.maple.service.approval;

/**
 * Thrown when an approval action violates configured rules (e.g. holiday calendar).
 */
public class ApprovalRulesViolationException extends RuntimeException {

    public ApprovalRulesViolationException(String message) {
        super(message);
    }
}

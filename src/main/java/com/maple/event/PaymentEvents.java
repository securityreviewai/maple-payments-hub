package com.maple.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event classes for payment lifecycle events.
 * 
 * These events are published to Kafka for downstream processing,
 * notifications, and integration with external systems.
 */
public class PaymentEvents {

    /**
     * Base class for all payment events.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static abstract class PaymentEvent {
        @JsonProperty("eventId")
        private String eventId = UUID.randomUUID().toString();
        
        @JsonProperty("timestamp")
        private OffsetDateTime timestamp = OffsetDateTime.now();
        
        @JsonProperty("paymentId")
        private UUID paymentId;
        
        @JsonProperty("paymentReference")
        private String paymentReference;
        
        @JsonProperty("eventType")
        private String eventType;

        public PaymentEvent(UUID paymentId, String paymentReference, String eventType) {
            this();
            this.paymentId = paymentId;
            this.paymentReference = paymentReference;
            this.eventType = eventType;
        }
    }

    /**
     * Event published when a payment is submitted.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSubmitted extends PaymentEvent {
        @JsonProperty("amountCents")
        private Long amountCents;
        
        @JsonProperty("currency")
        private String currency;
        
        @JsonProperty("status")
        private PaymentStatus status;
        
        @JsonProperty("approvalRequired")
        private Boolean approvalRequired;
        
        @JsonProperty("initiatedBy")
        private UUID initiatedBy;

        public PaymentSubmitted(UUID paymentId, String paymentReference, Long amountCents, 
                               String currency, PaymentStatus status, Boolean approvalRequired, 
                               UUID initiatedBy) {
            super(paymentId, paymentReference, "PAYMENT_SUBMITTED");
            this.amountCents = amountCents;
            this.currency = currency;
            this.status = status;
            this.approvalRequired = approvalRequired;
            this.initiatedBy = initiatedBy;
        }
    }

    /**
     * Event published when a payment status changes.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentStatusChanged extends PaymentEvent {
        @JsonProperty("fromStatus")
        private PaymentStatus fromStatus;
        
        @JsonProperty("toStatus")
        private PaymentStatus toStatus;
        
        @JsonProperty("changedBy")
        private UUID changedBy;
        
        @JsonProperty("reason")
        private String reason;

        public PaymentStatusChanged(UUID paymentId, String paymentReference, 
                                  PaymentStatus fromStatus, PaymentStatus toStatus, 
                                  UUID changedBy, String reason) {
            super(paymentId, paymentReference, "PAYMENT_STATUS_CHANGED");
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.changedBy = changedBy;
            this.reason = reason;
        }
    }

    /**
     * Event published when a payment is approved.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentApproved extends PaymentEvent {
        @JsonProperty("approvedBy")
        private UUID approvedBy;
        
        @JsonProperty("approvalNote")
        private String approvalNote;
        
        @JsonProperty("twoFactorVerified")
        private Boolean twoFactorVerified;

        public PaymentApproved(UUID paymentId, String paymentReference, UUID approvedBy, 
                              String approvalNote, Boolean twoFactorVerified) {
            super(paymentId, paymentReference, "PAYMENT_APPROVED");
            this.approvedBy = approvedBy;
            this.approvalNote = approvalNote;
            this.twoFactorVerified = twoFactorVerified;
        }
    }

    /**
     * Event published when a payment is rejected.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRejected extends PaymentEvent {
        @JsonProperty("rejectedBy")
        private UUID rejectedBy;
        
        @JsonProperty("rejectionReason")
        private String rejectionReason;

        public PaymentRejected(UUID paymentId, String paymentReference, UUID rejectedBy, 
                              String rejectionReason) {
            super(paymentId, paymentReference, "PAYMENT_REJECTED");
            this.rejectedBy = rejectedBy;
            this.rejectionReason = rejectionReason;
        }
    }

    /**
     * Event published when a payment is included in a batch.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentBatched extends PaymentEvent {
        @JsonProperty("batchId")
        private String batchId;
        
        @JsonProperty("batchSize")
        private Integer batchSize;
        
        @JsonProperty("iso20022Filename")
        private String iso20022Filename;

        public PaymentBatched(UUID paymentId, String paymentReference, String batchId, 
                             Integer batchSize, String iso20022Filename) {
            super(paymentId, paymentReference, "PAYMENT_BATCHED");
            this.batchId = batchId;
            this.batchSize = batchSize;
            this.iso20022Filename = iso20022Filename;
        }
    }

    /**
     * Event published when a payment is submitted to the clearing network.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSubmittedToClearing extends PaymentEvent {
        @JsonProperty("batchId")
        private String batchId;
        
        @JsonProperty("clearingReference")
        private String clearingReference;
        
        @JsonProperty("submittedVia")
        private String submittedVia; // SFTP, API, etc.

        public PaymentSubmittedToClearing(UUID paymentId, String paymentReference, 
                                         String batchId, String clearingReference, 
                                         String submittedVia) {
            super(paymentId, paymentReference, "PAYMENT_SUBMITTED_TO_CLEARING");
            this.batchId = batchId;
            this.clearingReference = clearingReference;
            this.submittedVia = submittedVia;
        }
    }

    /**
     * Event published when a payment is settled.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSettled extends PaymentEvent {
        @JsonProperty("settledAt")
        private OffsetDateTime settledAt;
        
        @JsonProperty("clearingReference")
        private String clearingReference;
        
        @JsonProperty("settlementMethod")
        private String settlementMethod;

        public PaymentSettled(UUID paymentId, String paymentReference, 
                             OffsetDateTime settledAt, String clearingReference, 
                             String settlementMethod) {
            super(paymentId, paymentReference, "PAYMENT_SETTLED");
            this.settledAt = settledAt;
            this.clearingReference = clearingReference;
            this.settlementMethod = settlementMethod;
        }
    }

    /**
     * Event published when a payment fails.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailed extends PaymentEvent {
        @JsonProperty("failureReason")
        private String failureReason;
        
        @JsonProperty("errorCode")
        private String errorCode;
        
        @JsonProperty("retryable")
        private Boolean retryable;

        public PaymentFailed(UUID paymentId, String paymentReference, 
                            String failureReason, String errorCode, Boolean retryable) {
            super(paymentId, paymentReference, "PAYMENT_FAILED");
            this.failureReason = failureReason;
            this.errorCode = errorCode;
            this.retryable = retryable;
        }
    }

    /**
     * Event published when a payment is cancelled.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentCancelled extends PaymentEvent {
        @JsonProperty("cancelledBy")
        private UUID cancelledBy;
        
        @JsonProperty("cancellationReason")
        private String cancellationReason;

        public PaymentCancelled(UUID paymentId, String paymentReference, 
                               UUID cancelledBy, String cancellationReason) {
            super(paymentId, paymentReference, "PAYMENT_CANCELLED");
            this.cancelledBy = cancelledBy;
            this.cancellationReason = cancellationReason;
        }
    }

    /**
     * Event published for reconciliation purposes.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentReconciliationRequired extends PaymentEvent {
        @JsonProperty("expectedStatus")
        private PaymentStatus expectedStatus;
        
        @JsonProperty("actualStatus")
        private PaymentStatus actualStatus;
        
        @JsonProperty("daysSinceSubmission")
        private Integer daysSinceSubmission;

        public PaymentReconciliationRequired(UUID paymentId, String paymentReference, 
                                           PaymentStatus expectedStatus, PaymentStatus actualStatus, 
                                           Integer daysSinceSubmission) {
            super(paymentId, paymentReference, "PAYMENT_RECONCILIATION_REQUIRED");
            this.expectedStatus = expectedStatus;
            this.actualStatus = actualStatus;
            this.daysSinceSubmission = daysSinceSubmission;
        }
    }

    /**
     * First approver recorded for dual control; payment remains pending second approval.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDualApprovalStep extends PaymentEvent {
        @JsonProperty("stepApprover")
        private UUID stepApprover;

        @JsonProperty("requiredApprovers")
        private Integer requiredApprovers;

        @JsonProperty("approvalNote")
        private String approvalNote;

        public PaymentDualApprovalStep(UUID paymentId, String paymentReference, UUID stepApprover,
                                       Integer requiredApprovers, String approvalNote) {
            super(paymentId, paymentReference, "PAYMENT_DUAL_APPROVAL_STEP");
            this.stepApprover = stepApprover;
            this.requiredApprovers = requiredApprovers;
            this.approvalNote = approvalNote;
        }
    }

    /**
     * Published when a pending approval exceeds configured business-time thresholds (stuck approval).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentApprovalEscalated extends PaymentEvent {
        @JsonProperty("escalationLevel")
        private Integer escalationLevel;

        @JsonProperty("pendingSince")
        private OffsetDateTime pendingSince;

        @JsonProperty("reason")
        private String reason;

        public PaymentApprovalEscalated(UUID paymentId, String paymentReference, Integer escalationLevel,
                                       OffsetDateTime pendingSince, String reason) {
            super(paymentId, paymentReference, "PAYMENT_APPROVAL_ESCALATED");
            this.escalationLevel = escalationLevel;
            this.pendingSince = pendingSince;
            this.reason = reason;
        }
    }
}

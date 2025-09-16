package com.maple.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a payment instruction.
 * 
 * This entity captures the complete lifecycle of a payment from initiation
 * through settlement, including approval workflow and audit trail.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_payment_reference", columnList = "paymentReference"),
    @Index(name = "idx_payments_idempotency_key", columnList = "idempotencyKey"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_initiated_by", columnList = "initiatedBy"),
    @Index(name = "idx_payments_created_at", columnList = "createdAt")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 64)
    @NotBlank
    @Size(max = 64)
    private String paymentReference;

    @Column(name = "amount_cents", nullable = false)
    @NotNull
    @Positive
    private Long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency = "USD";

    @Column(name = "debtor_account", nullable = false, length = 64)
    @NotBlank
    @Size(max = 64)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false, length = 64)
    @NotBlank
    @Size(max = 64)
    private String creditorAccount;

    @Column(name = "creditor_name", length = 140)
    @Size(max = 140)
    private String creditorName;

    @Column(name = "payment_purpose", length = 500)
    @Size(max = 500)
    private String paymentPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "initiated_by", nullable = false)
    @NotNull
    private UUID initiatedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approval_required", nullable = false)
    private Boolean approvalRequired = false;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "iso20022_filename")
    private String iso20022Filename;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "idempotency_key", length = 128)
    @Size(max = 128)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public Payment() {}

    public Payment(String paymentReference, Long amountCents, String currency,
                   String debtorAccount, String creditorAccount, UUID initiatedBy) {
        this.paymentReference = paymentReference;
        this.amountCents = amountCents;
        this.currency = currency;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.initiatedBy = initiatedBy;
    }

    // Business methods
    
    /**
     * Determines if this payment requires manual approval based on amount and initiator rules.
     */
    public void evaluateApprovalRequirement(boolean initiatorRequiresApproval, long approvalThresholdCents) {
        this.approvalRequired = initiatorRequiresApproval || this.amountCents > approvalThresholdCents;
        if (this.approvalRequired && this.status == PaymentStatus.CREATED) {
            this.status = PaymentStatus.PENDING_APPROVAL;
        }
    }

    /**
     * Approves the payment by the specified approver.
     */
    public void approve(UUID approverId) {
        if (!this.approvalRequired) {
            throw new IllegalStateException("Payment does not require approval");
        }
        if (this.status != PaymentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Payment is not in pending approval status");
        }
        this.approvedBy = approverId;
        this.status = PaymentStatus.APPROVED;
    }

    /**
     * Rejects the payment.
     */
    public void reject() {
        if (this.status != PaymentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Payment is not in pending approval status");
        }
        this.status = PaymentStatus.REJECTED;
    }

    /**
     * Marks payment as submitted to clearing network.
     */
    public void markSubmitted(String batchId, String iso20022Filename) {
        if (this.status != PaymentStatus.APPROVED && 
            !(this.status == PaymentStatus.CREATED && !this.approvalRequired)) {
            throw new IllegalStateException("Payment is not ready for submission");
        }
        this.status = PaymentStatus.SUBMITTED;
        this.batchId = batchId;
        this.iso20022Filename = iso20022Filename;
        this.submittedAt = OffsetDateTime.now();
    }

    /**
     * Marks payment as settled.
     */
    public void markSettled() {
        if (this.status != PaymentStatus.SUBMITTED) {
            throw new IllegalStateException("Payment must be submitted before it can be settled");
        }
        this.status = PaymentStatus.SETTLED;
        this.settledAt = OffsetDateTime.now();
    }

    /**
     * Marks payment as failed with reason.
     */
    public void markFailed() {
        if (this.status != PaymentStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted payments can fail");
        }
        this.status = PaymentStatus.FAILED;
    }

    /**
     * Cancels the payment if it's in a cancellable state.
     */
    public void cancel() {
        if (!this.status.canBeCancelled()) {
            throw new IllegalStateException("Payment cannot be cancelled in current status: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDebtorAccount() { return debtorAccount; }
    public void setDebtorAccount(String debtorAccount) { this.debtorAccount = debtorAccount; }

    public String getCreditorAccount() { return creditorAccount; }
    public void setCreditorAccount(String creditorAccount) { this.creditorAccount = creditorAccount; }

    public String getCreditorName() { return creditorName; }
    public void setCreditorName(String creditorName) { this.creditorName = creditorName; }

    public String getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(String paymentPurpose) { this.paymentPurpose = paymentPurpose; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public UUID getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(UUID initiatedBy) { this.initiatedBy = initiatedBy; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Boolean getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(OffsetDateTime settledAt) { this.settledAt = settledAt; }

    public String getIso20022Filename() { return iso20022Filename; }
    public void setIso20022Filename(String iso20022Filename) { this.iso20022Filename = iso20022Filename; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id) && 
               Objects.equals(paymentReference, payment.paymentReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, paymentReference);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", paymentReference='" + paymentReference + '\'' +
                ", amountCents=" + amountCents +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}

package com.maple.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing an approval decision for a payment.
 * 
 * Each approval captures who made the decision, what decision was made,
 * and when it occurred. Multiple approvals can exist for a single payment
 * in complex approval workflows.
 */
@Entity
@Table(name = "approvals", 
       indexes = {
           @Index(name = "idx_approvals_payment_id", columnList = "paymentId"),
           @Index(name = "idx_approvals_approver_id", columnList = "approverId")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_approval_payment_approver_action", 
                           columnNames = {"paymentId", "approverId", "action"})
       })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    @NotNull
    private UUID paymentId;

    @Column(name = "approver_id", nullable = false)
    @NotNull
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    @NotNull
    private ApprovalAction action;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "two_factor_verified", nullable = false)
    private Boolean twoFactorVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public Approval() {}

    public Approval(UUID paymentId, UUID approverId, ApprovalAction action) {
        this.paymentId = paymentId;
        this.approverId = approverId;
        this.action = action;
    }

    public Approval(UUID paymentId, UUID approverId, ApprovalAction action, String note) {
        this(paymentId, approverId, action);
        this.note = note;
    }

    // Business methods
    
    /**
     * Verifies that two-factor authentication was completed for this approval.
     */
    public void verifyTwoFactor() {
        this.twoFactorVerified = true;
    }

    /**
     * @return true if this is an approval (not rejection)
     */
    public boolean isApproval() {
        return action == ApprovalAction.APPROVED;
    }

    /**
     * @return true if this is a rejection (not approval)
     */
    public boolean isRejection() {
        return action == ApprovalAction.REJECTED;
    }

    /**
     * Updates the approval note.
     * 
     * @param newNote New note to set
     */
    public void updateNote(String newNote) {
        this.note = newNote;
    }

    /**
     * @return true if this approval has a note
     */
    public boolean hasNote() {
        return note != null && !note.trim().isEmpty();
    }

    /**
     * @return true if two-factor authentication was verified for this approval
     */
    public boolean isSecurelyVerified() {
        return twoFactorVerified != null && twoFactorVerified;
    }

    /**
     * Gets a summary description of this approval.
     * 
     * @return Summary string
     */
    public String getSummary() {
        String actionDesc = isApproval() ? "Approved" : "Rejected";
        String verified = isSecurelyVerified() ? " (2FA verified)" : "";
        return actionDesc + verified;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public UUID getApproverId() { return approverId; }
    public void setApproverId(UUID approverId) { this.approverId = approverId; }

    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Boolean getTwoFactorVerified() { return twoFactorVerified; }
    public void setTwoFactorVerified(Boolean twoFactorVerified) { this.twoFactorVerified = twoFactorVerified; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Approval approval = (Approval) o;
        return Objects.equals(id, approval.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Approval{" +
                "id=" + id +
                ", paymentId=" + paymentId +
                ", approverId=" + approverId +
                ", action=" + action +
                ", twoFactorVerified=" + twoFactorVerified +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Enumeration for approval actions.
     */
    public enum ApprovalAction {
        APPROVED, REJECTED
    }
}

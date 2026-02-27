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
 * JPA entity representing dynamic approval policies for payment processing.
 * 
 * Defines maker-checker rules based on:
 * - Payment amount thresholds
 * - Currency and geography 
 * - Counterparty risk ratings
 * - N-of-M approval requirements
 * - Time-based restrictions
 */
@Entity
@Table(name = "approval_policies", indexes = {
    @Index(name = "idx_approval_policies_active", columnList = "isActive"),
    @Index(name = "idx_approval_policies_amount_range", columnList = "minAmountCents, maxAmountCents"),
    @Index(name = "idx_approval_policies_currency", columnList = "currency"),
    @Index(name = "idx_approval_policies_priority", columnList = "priority")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_name", nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String policyName;

    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "priority", nullable = false)
    @NotNull
    @Positive
    private Integer priority; // Lower number = higher priority

    // Amount thresholds
    @Column(name = "min_amount_cents")
    private Long minAmountCents;

    @Column(name = "max_amount_cents")
    private Long maxAmountCents;

    @Column(name = "currency", length = 3)
    @Size(max = 3)
    private String currency; // NULL = applies to all currencies

    // Geographic restrictions
    @Column(name = "originating_country", length = 2)
    @Size(max = 2)
    private String originatingCountry; // ISO 2-letter country code

    @Column(name = "destination_country", length = 2)
    @Size(max = 2)
    private String destinationCountry;

    // Counterparty risk
    @Enumerated(EnumType.STRING)
    @Column(name = "max_counterparty_risk")
    private CounterpartyRiskRating maxCounterpartyRisk;

    // Payment type restrictions
    @Column(name = "payment_types", columnDefinition = "text[]")
    private String[] paymentTypes; // ACH, WIRE, FEDWIRE, CHIPS, etc.

    // Approval requirements
    @Column(name = "required_approvers", nullable = false)
    @NotNull
    @Positive
    private Integer requiredApprovers; // N in N-of-M

    @Column(name = "eligible_approvers", nullable = false)
    @NotNull
    @Positive
    private Integer eligibleApprovers; // M in N-of-M

    @Column(name = "required_roles", columnDefinition = "text[]")
    private String[] requiredRoles; // Roles that can approve

    @Column(name = "excluded_roles", columnDefinition = "text[]")
    private String[] excludedRoles; // Roles that cannot approve

    @Column(name = "require_dual_control", nullable = false)
    private Boolean requireDualControl = false; // Initiator cannot approve

    @Column(name = "require_senior_approval", nullable = false)
    private Boolean requireSeniorApproval = false; // Requires manager+ role

    // Time-based restrictions
    @Column(name = "business_hours_only", nullable = false)
    private Boolean businessHoursOnly = false;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    // Escalation settings
    @Column(name = "escalation_hours")
    private Integer escalationHours; // Auto-escalate after X hours

    @Column(name = "escalation_roles", columnDefinition = "text[]")
    private String[] escalationRoles; // Roles to escalate to

    // Risk scoring
    @Column(name = "risk_score_threshold")
    private Integer riskScoreThreshold; // Minimum risk score to trigger policy

    @Column(name = "additional_conditions", columnDefinition = "jsonb")
    private String additionalConditions; // JSON for complex conditions

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    @NotNull
    private UUID createdBy;

    @Column(name = "last_modified_by")
    private UUID lastModifiedBy;

    // Constructors
    public ApprovalPolicy() {}

    public ApprovalPolicy(String policyName, Integer priority, Integer requiredApprovers, 
                         Integer eligibleApprovers, UUID createdBy) {
        this.policyName = policyName;
        this.priority = priority;
        this.requiredApprovers = requiredApprovers;
        this.eligibleApprovers = eligibleApprovers;
        this.createdBy = createdBy;
    }

    // Business methods

    /**
     * Checks if this policy applies to a given payment.
     */
    public boolean appliesTo(Payment payment, String paymentType, CounterpartyRiskRating counterpartyRisk) {
        if (!isActive) return false;

        // Check amount range
        if (minAmountCents != null && payment.getAmountCents() < minAmountCents) return false;
        if (maxAmountCents != null && payment.getAmountCents() > maxAmountCents) return false;

        // Check currency
        if (currency != null && !currency.equals(payment.getCurrency())) return false;

        // Check counterparty risk
        if (maxCounterpartyRisk != null && counterpartyRisk != null) {
            if (counterpartyRisk.ordinal() > maxCounterpartyRisk.ordinal()) return false;
        }

        // Check payment type
        if (paymentTypes != null && paymentTypes.length > 0) {
            boolean typeMatches = false;
            for (String type : paymentTypes) {
                if (type.equals(paymentType)) {
                    typeMatches = true;
                    break;
                }
            }
            if (!typeMatches) return false;
        }

        // Check time validity
        OffsetDateTime now = OffsetDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;

        return true;
    }

    /**
     * Checks if a user role can approve under this policy.
     */
    public boolean canRoleApprove(String userRole) {
        // Check excluded roles first
        if (excludedRoles != null) {
            for (String excludedRole : excludedRoles) {
                if (excludedRole.equals(userRole)) return false;
            }
        }

        // Check required roles
        if (requiredRoles != null && requiredRoles.length > 0) {
            for (String requiredRole : requiredRoles) {
                if (requiredRole.equals(userRole)) return true;
            }
            return false; // Role not in required list
        }

        return true; // No specific role requirements
    }

    /**
     * Checks if this policy requires escalation based on time elapsed.
     */
    public boolean requiresEscalation(OffsetDateTime submissionTime) {
        if (escalationHours == null) return false;
        
        OffsetDateTime escalationDeadline = submissionTime.plusHours(escalationHours);
        return OffsetDateTime.now().isAfter(escalationDeadline);
    }

    /**
     * Validates that the policy configuration is consistent.
     */
    public boolean isValid() {
        // Required approvers cannot exceed eligible approvers
        if (requiredApprovers > eligibleApprovers) return false;

        // Amount range validation
        if (minAmountCents != null && maxAmountCents != null && minAmountCents > maxAmountCents) {
            return false;
        }

        // Date range validation
        if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
            return false;
        }

        return true;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Long getMinAmountCents() { return minAmountCents; }
    public void setMinAmountCents(Long minAmountCents) { this.minAmountCents = minAmountCents; }

    public Long getMaxAmountCents() { return maxAmountCents; }
    public void setMaxAmountCents(Long maxAmountCents) { this.maxAmountCents = maxAmountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getOriginatingCountry() { return originatingCountry; }
    public void setOriginatingCountry(String originatingCountry) { this.originatingCountry = originatingCountry; }

    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }

    public CounterpartyRiskRating getMaxCounterpartyRisk() { return maxCounterpartyRisk; }
    public void setMaxCounterpartyRisk(CounterpartyRiskRating maxCounterpartyRisk) { 
        this.maxCounterpartyRisk = maxCounterpartyRisk; 
    }

    public String[] getPaymentTypes() { return paymentTypes; }
    public void setPaymentTypes(String[] paymentTypes) { this.paymentTypes = paymentTypes; }

    public Integer getRequiredApprovers() { return requiredApprovers; }
    public void setRequiredApprovers(Integer requiredApprovers) { this.requiredApprovers = requiredApprovers; }

    public Integer getEligibleApprovers() { return eligibleApprovers; }
    public void setEligibleApprovers(Integer eligibleApprovers) { this.eligibleApprovers = eligibleApprovers; }

    public String[] getRequiredRoles() { return requiredRoles; }
    public void setRequiredRoles(String[] requiredRoles) { this.requiredRoles = requiredRoles; }

    public String[] getExcludedRoles() { return excludedRoles; }
    public void setExcludedRoles(String[] excludedRoles) { this.excludedRoles = excludedRoles; }

    public Boolean getRequireDualControl() { return requireDualControl; }
    public void setRequireDualControl(Boolean requireDualControl) { this.requireDualControl = requireDualControl; }

    public Boolean getRequireSeniorApproval() { return requireSeniorApproval; }
    public void setRequireSeniorApproval(Boolean requireSeniorApproval) { 
        this.requireSeniorApproval = requireSeniorApproval; 
    }

    public Boolean getBusinessHoursOnly() { return businessHoursOnly; }
    public void setBusinessHoursOnly(Boolean businessHoursOnly) { this.businessHoursOnly = businessHoursOnly; }

    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }

    public OffsetDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(OffsetDateTime validUntil) { this.validUntil = validUntil; }

    public Integer getEscalationHours() { return escalationHours; }
    public void setEscalationHours(Integer escalationHours) { this.escalationHours = escalationHours; }

    public String[] getEscalationRoles() { return escalationRoles; }
    public void setEscalationRoles(String[] escalationRoles) { this.escalationRoles = escalationRoles; }

    public Integer getRiskScoreThreshold() { return riskScoreThreshold; }
    public void setRiskScoreThreshold(Integer riskScoreThreshold) { this.riskScoreThreshold = riskScoreThreshold; }

    public String getAdditionalConditions() { return additionalConditions; }
    public void setAdditionalConditions(String additionalConditions) { this.additionalConditions = additionalConditions; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(UUID lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalPolicy that = (ApprovalPolicy) o;
        return Objects.equals(id, that.id) && Objects.equals(policyName, that.policyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, policyName);
    }

    @Override
    public String toString() {
        return "ApprovalPolicy{" +
                "id=" + id +
                ", policyName='" + policyName + '\'' +
                ", priority=" + priority +
                ", requiredApprovers=" + requiredApprovers +
                ", eligibleApprovers=" + eligibleApprovers +
                ", isActive=" + isActive +
                '}';
    }

    /**
     * Enumeration for counterparty risk ratings.
     */
    public enum CounterpartyRiskRating {
        LOW,        // Established relationships, high credit rating
        MEDIUM,     // Standard commercial relationships
        HIGH,       // New relationships or lower credit rating
        RESTRICTED  // Enhanced due diligence required
    }
}

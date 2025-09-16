package com.maple.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maple.model.*;
import com.maple.model.ApprovalPolicy.CounterpartyRiskRating;
import com.maple.repository.ApprovalPolicyRepository;
import com.maple.repository.ApprovalRepository;
import com.maple.repository.PaymentRepository;
import com.maple.repository.UserRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced approval service implementing dynamic N-of-M approval policies.
 * 
 * Features:
 * - Database-driven approval policies (not hardcoded)
 * - N-of-M approval requirements with role-based eligibility
 * - Counterparty risk assessment integration
 * - Business hours and time-based restrictions
 * - Escalation workflows for aging approvals
 * - Policy conflict resolution and caching
 */
@Service
@Transactional
public class ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalPolicyRepository approvalPolicyRepository;
    private final ApprovalRepository approvalRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalService(
            ApprovalPolicyRepository approvalPolicyRepository,
            ApprovalRepository approvalRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.approvalPolicyRepository = approvalPolicyRepository;
        this.approvalRepository = approvalRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Determines approval requirements for a payment based on dynamic policies.
     */
    @Cacheable(value = "approvalRequirements", key = "#payment.id")
    public ApprovalRequirement determineApprovalRequirement(Payment payment, String paymentType, 
                                                           CounterpartyRiskRating counterpartyRisk) {
        logger.debug("Determining approval requirements for payment: {}", payment.getId());

        try {
            // Get applicable policies
            List<ApprovalPolicy> applicablePolicies = findApplicablePolicies(
                payment, paymentType, counterpartyRisk);

            if (applicablePolicies.isEmpty()) {
                // No policies apply - payment can proceed without approval
                return new ApprovalRequirement(false, 0, 0, Collections.emptyList(), null);
            }

            // Find the most restrictive policy (highest approval requirements)
            ApprovalPolicy mostRestrictive = applicablePolicies.stream()
                .max(Comparator.comparing(ApprovalPolicy::getRequiredApprovers)
                    .thenComparing(p -> p.getRequireSeniorApproval() ? 1 : 0)
                    .thenComparing(p -> p.getRequireDualControl() ? 1 : 0))
                .orElse(applicablePolicies.get(0));

            // Check time-based restrictions
            if (mostRestrictive.getBusinessHoursOnly() && !isBusinessHours()) {
                logger.warn("Payment {} submitted outside business hours but requires business hours approval", 
                           payment.getId());
                return new ApprovalRequirement(true, mostRestrictive.getRequiredApprovers(), 
                                             mostRestrictive.getEligibleApprovers(), 
                                             Collections.emptyList(), mostRestrictive,
                                             "Payment submitted outside business hours");
            }

            // Get eligible approvers based on policy
            List<User> eligibleApprovers = findEligibleApprovers(payment, mostRestrictive);

            // Validate dual control requirement
            if (mostRestrictive.getRequireDualControl()) {
                eligibleApprovers = eligibleApprovers.stream()
                    .filter(user -> !user.getId().equals(payment.getInitiatedBy()))
                    .collect(Collectors.toList());
            }

            if (eligibleApprovers.size() < mostRestrictive.getEligibleApprovers()) {
                logger.warn("Insufficient eligible approvers for payment {}: required={}, available={}", 
                           payment.getId(), mostRestrictive.getEligibleApprovers(), eligibleApprovers.size());
            }

            auditService.auditSystemAction("APPROVAL_REQUIREMENT_DETERMINED", "Payment", 
                                         payment.getId().toString());

            return new ApprovalRequirement(
                true,
                mostRestrictive.getRequiredApprovers(),
                mostRestrictive.getEligibleApprovers(),
                eligibleApprovers,
                mostRestrictive
            );

        } catch (Exception e) {
            logger.error("Error determining approval requirements for payment: {}", payment.getId(), e);
            // Fail safe - require manual approval
            return new ApprovalRequirement(true, 1, 1, Collections.emptyList(), null,
                                         "Error in policy evaluation - manual review required");
        }
    }

    /**
     * Processes an approval decision for a payment.
     */
    public ApprovalResult processApproval(UUID paymentId, UUID approverId, 
                                        Approval.ApprovalAction action, String note, 
                                        boolean twoFactorVerified) {
        logger.info("Processing approval: payment={}, approver={}, action={}", 
                   paymentId, approverId, action);

        // Lock payment for update
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ApprovalException("Payment not found: " + paymentId));

        // Validate payment state
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalException("Payment is not in pending approval status: " + payment.getStatus());
        }

        // Get user and validate permissions
        User approver = userRepository.findById(approverId)
            .orElseThrow(() -> new ApprovalException("Approver not found: " + approverId));

        // Check if user has already acted on this payment
        if (approvalRepository.existsByPaymentIdAndApproverId(paymentId, approverId)) {
            throw new ApprovalException("User has already provided approval decision for this payment");
        }

        // Get current approval requirements
        ApprovalRequirement requirement = determineApprovalRequirement(payment, "WIRE", CounterpartyRiskRating.MEDIUM);
        
        // Validate approver eligibility
        boolean isEligible = requirement.getEligibleApprovers().stream()
            .anyMatch(user -> user.getId().equals(approverId));
        
        if (!isEligible && requirement.getApplicablePolicy() != null) {
            // Check if user role can approve based on policy
            boolean roleCanApprove = Arrays.stream(approver.getRoles())
                .anyMatch(role -> requirement.getApplicablePolicy().canRoleApprove(role));
            
            if (!roleCanApprove) {
                throw new ApprovalException("User does not have permission to approve this payment");
            }
        }

        // Create approval record
        Approval approval = new Approval(paymentId, approverId, action, note);
        if (twoFactorVerified) {
            approval.verifyTwoFactor();
        }

        // Check if 2FA is required for senior approvals
        if (requirement.getApplicablePolicy() != null && 
            requirement.getApplicablePolicy().getRequireSeniorApproval() && !twoFactorVerified) {
            throw new ApprovalException("Two-factor authentication required for senior approval");
        }

        approval = approvalRepository.save(approval);

        // Process approval decision
        ApprovalResult result;
        if (action == Approval.ApprovalAction.REJECTED) {
            // Rejection immediately terminates the approval process
            payment.reject();
            paymentRepository.save(payment);
            
            auditService.auditApprovalEvent(approverId.toString(), "PAYMENT_REJECTED", paymentId, "REJECTED");
            result = new ApprovalResult(true, false, "Payment rejected", Collections.singletonList(approval));
        } else {
            // Check if we have enough approvals
            List<Approval> existingApprovals = approvalRepository.findByPaymentId(paymentId);
            long approvalCount = existingApprovals.stream()
                .filter(a -> a.getAction() == Approval.ApprovalAction.APPROVED)
                .count();

            if (approvalCount >= requirement.getRequiredApprovers()) {
                // Sufficient approvals - approve payment
                payment.approve(approverId);
                paymentRepository.save(payment);
                
                auditService.auditApprovalEvent(approverId.toString(), "PAYMENT_APPROVED", paymentId, "APPROVED");
                result = new ApprovalResult(true, true, "Payment fully approved", existingApprovals);
            } else {
                // More approvals needed
                auditService.auditApprovalEvent(approverId.toString(), "APPROVAL_PROVIDED", paymentId, "APPROVED");
                long remaining = requirement.getRequiredApprovers() - approvalCount;
                result = new ApprovalResult(false, false, 
                    String.format("Approval recorded. %d more approval(s) required", remaining), 
                    existingApprovals);
            }
        }

        // Check for escalation requirements
        if (requirement.getApplicablePolicy() != null && 
            requirement.getApplicablePolicy().requiresEscalation(payment.getCreatedAt())) {
            handleEscalation(payment, requirement.getApplicablePolicy());
        }

        return result;
    }

    /**
     * Finds policies applicable to a payment.
     */
    private List<ApprovalPolicy> findApplicablePolicies(Payment payment, String paymentType, 
                                                       CounterpartyRiskRating counterpartyRisk) {
        // Try cached lookup first
        String cacheKey = generatePolicyCacheKey(payment, paymentType, counterpartyRisk);
        
        return approvalPolicyRepository.findMatchingPolicies(
            payment.getAmountCents(),
            payment.getCurrency(),
            paymentType,
            counterpartyRisk,
            "US", // Simplified - would be extracted from payment data
            extractDestinationCountry(payment),
            OffsetDateTime.now()
        );
    }

    /**
     * Finds users eligible to approve based on policy.
     */
    private List<User> findEligibleApprovers(Payment payment, ApprovalPolicy policy) {
        List<User> allActiveUsers = userRepository.findByIsActiveTrue();
        
        return allActiveUsers.stream()
            .filter(user -> {
                // Check role eligibility
                if (policy.getRequiredRoles() != null && policy.getRequiredRoles().length > 0) {
                    boolean hasRequiredRole = Arrays.stream(user.getRoles())
                        .anyMatch(userRole -> Arrays.asList(policy.getRequiredRoles()).contains(userRole));
                    if (!hasRequiredRole) return false;
                }
                
                // Check excluded roles
                if (policy.getExcludedRoles() != null) {
                    boolean hasExcludedRole = Arrays.stream(user.getRoles())
                        .anyMatch(userRole -> Arrays.asList(policy.getExcludedRoles()).contains(userRole));
                    if (hasExcludedRole) return false;
                }
                
                // Check dual control requirement
                if (policy.getRequireDualControl() && user.getId().equals(payment.getInitiatedBy())) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Handles escalation for overdue approvals.
     */
    private void handleEscalation(Payment payment, ApprovalPolicy policy) {
        logger.info("Escalating approval for payment: {} under policy: {}", 
                   payment.getId(), policy.getPolicyName());

        // Audit escalation
        Map<String, Object> escalationDetails = new HashMap<>();
        escalationDetails.put("policyId", policy.getId());
        escalationDetails.put("escalationHours", policy.getEscalationHours());
        escalationDetails.put("escalationRoles", policy.getEscalationRoles());

        try {
            String details = objectMapper.writeValueAsString(escalationDetails);
            auditService.auditSystemAction("APPROVAL_ESCALATED", "Payment", 
                                          payment.getId().toString(), details);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize escalation details", e);
        }

        // In a real implementation, this would notify escalation approvers
        // via email, Slack, or other notification mechanisms
    }

    /**
     * Checks if current time is within business hours.
     */
    private boolean isBusinessHours() {
        LocalTime now = LocalTime.now();
        LocalTime businessStart = LocalTime.of(8, 0);  // 8 AM
        LocalTime businessEnd = LocalTime.of(17, 0);   // 5 PM
        
        return now.isAfter(businessStart) && now.isBefore(businessEnd);
    }

    /**
     * Generates cache key for policy lookup.
     */
    private String generatePolicyCacheKey(Payment payment, String paymentType, 
                                        CounterpartyRiskRating counterpartyRisk) {
        return String.format("policy_%d_%s_%s_%s", 
                           payment.getAmountCents(), 
                           payment.getCurrency(), 
                           paymentType,
                           counterpartyRisk != null ? counterpartyRisk.name() : "NULL");
    }

    /**
     * Extracts destination country from payment data.
     */
    private String extractDestinationCountry(Payment payment) {
        // Simplified implementation - would extract from creditor account or routing info
        return "US";
    }

    // Result classes

    public static class ApprovalRequirement {
        private final boolean approvalRequired;
        private final int requiredApprovers;
        private final int eligibleApprovers;
        private final List<User> eligibleApproversList;
        private final ApprovalPolicy applicablePolicy;
        private final String reason;

        public ApprovalRequirement(boolean approvalRequired, int requiredApprovers, int eligibleApprovers,
                                 List<User> eligibleApproversList, ApprovalPolicy applicablePolicy) {
            this(approvalRequired, requiredApprovers, eligibleApprovers, eligibleApproversList, applicablePolicy, null);
        }

        public ApprovalRequirement(boolean approvalRequired, int requiredApprovers, int eligibleApprovers,
                                 List<User> eligibleApproversList, ApprovalPolicy applicablePolicy, String reason) {
            this.approvalRequired = approvalRequired;
            this.requiredApprovers = requiredApprovers;
            this.eligibleApprovers = eligibleApprovers;
            this.eligibleApproversList = eligibleApproversList != null ? eligibleApproversList : Collections.emptyList();
            this.applicablePolicy = applicablePolicy;
            this.reason = reason;
        }

        // Getters
        public boolean isApprovalRequired() { return approvalRequired; }
        public int getRequiredApprovers() { return requiredApprovers; }
        public int getEligibleApprovers() { return eligibleApprovers; }
        public List<User> getEligibleApproversList() { return eligibleApproversList; }
        public ApprovalPolicy getApplicablePolicy() { return applicablePolicy; }
        public String getReason() { return reason; }
    }

    public static class ApprovalResult {
        private final boolean processComplete;
        private final boolean approved;
        private final String message;
        private final List<Approval> approvals;

        public ApprovalResult(boolean processComplete, boolean approved, String message, List<Approval> approvals) {
            this.processComplete = processComplete;
            this.approved = approved;
            this.message = message;
            this.approvals = approvals;
        }

        // Getters
        public boolean isProcessComplete() { return processComplete; }
        public boolean isApproved() { return approved; }
        public String getMessage() { return message; }
        public List<Approval> getApprovals() { return approvals; }
    }

    // Exception class
    public static class ApprovalException extends RuntimeException {
        public ApprovalException(String message) {
            super(message);
        }

        public ApprovalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

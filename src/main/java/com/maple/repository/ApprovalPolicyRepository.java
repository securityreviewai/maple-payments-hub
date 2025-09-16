package com.maple.repository;

import com.maple.model.ApprovalPolicy;
import com.maple.model.ApprovalPolicy.CounterpartyRiskRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ApprovalPolicy entity operations.
 * 
 * Provides data access methods for dynamic approval policy management
 * including policy matching, caching, and audit trail queries.
 */
@Repository
public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, UUID> {

    /**
     * Find active policies ordered by priority.
     */
    List<ApprovalPolicy> findByIsActiveTrueOrderByPriorityAsc();

    /**
     * Find policies by name (case-insensitive).
     */
    Optional<ApprovalPolicy> findByPolicyNameIgnoreCase(String policyName);

    /**
     * Find policies applicable to a payment amount and currency.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.minAmountCents IS NULL OR p.minAmountCents <= :amountCents) " +
           "AND (p.maxAmountCents IS NULL OR p.maxAmountCents >= :amountCents) " +
           "AND (p.currency IS NULL OR p.currency = :currency) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findApplicablePoliciesByAmountAndCurrency(
            @Param("amountCents") Long amountCents,
            @Param("currency") String currency);

    /**
     * Find policies applicable to a specific payment type.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.paymentTypes IS NULL OR :paymentType = ANY(p.paymentTypes)) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findApplicablePoliciesByPaymentType(@Param("paymentType") String paymentType);

    /**
     * Find policies applicable to counterparty risk level.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.maxCounterpartyRisk IS NULL OR p.maxCounterpartyRisk >= :riskRating) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findApplicablePoliciesByRisk(@Param("riskRating") CounterpartyRiskRating riskRating);

    /**
     * Find comprehensive policy matches for payment characteristics.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.minAmountCents IS NULL OR p.minAmountCents <= :amountCents) " +
           "AND (p.maxAmountCents IS NULL OR p.maxAmountCents >= :amountCents) " +
           "AND (p.currency IS NULL OR p.currency = :currency) " +
           "AND (p.paymentTypes IS NULL OR :paymentType = ANY(p.paymentTypes)) " +
           "AND (p.maxCounterpartyRisk IS NULL OR p.maxCounterpartyRisk >= :riskRating) " +
           "AND (p.originatingCountry IS NULL OR p.originatingCountry = :originCountry) " +
           "AND (p.destinationCountry IS NULL OR p.destinationCountry = :destCountry) " +
           "AND (p.validFrom IS NULL OR p.validFrom <= :currentTime) " +
           "AND (p.validUntil IS NULL OR p.validUntil >= :currentTime) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findMatchingPolicies(
            @Param("amountCents") Long amountCents,
            @Param("currency") String currency,
            @Param("paymentType") String paymentType,
            @Param("riskRating") CounterpartyRiskRating riskRating,
            @Param("originCountry") String originCountry,
            @Param("destCountry") String destCountry,
            @Param("currentTime") OffsetDateTime currentTime);

    /**
     * Find policies that allow a specific role to approve.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.requiredRoles IS NULL OR :userRole = ANY(p.requiredRoles)) " +
           "AND (p.excludedRoles IS NULL OR :userRole != ALL(p.excludedRoles)) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findPoliciesForRole(@Param("userRole") String userRole);

    /**
     * Find policies requiring escalation based on age.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND p.escalationHours IS NOT NULL " +
           "AND p.escalationHours > 0")
    List<ApprovalPolicy> findPoliciesWithEscalation();

    /**
     * Find policies modified after a specific date.
     */
    List<ApprovalPolicy> findByUpdatedAtAfter(OffsetDateTime since);

    /**
     * Find policies created by a specific user.
     */
    List<ApprovalPolicy> findByCreatedBy(UUID createdBy);

    /**
     * Find policies by priority range.
     */
    List<ApprovalPolicy> findByIsActiveTrueAndPriorityBetweenOrderByPriorityAsc(
            Integer minPriority, Integer maxPriority);

    /**
     * Count active policies.
     */
    Long countByIsActiveTrue();

    /**
     * Find policies expiring soon.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND p.validUntil IS NOT NULL " +
           "AND p.validUntil BETWEEN :now AND :warningTime " +
           "ORDER BY p.validUntil ASC")
    List<ApprovalPolicy> findPoliciesExpiringSoon(@Param("now") OffsetDateTime now,
                                                 @Param("warningTime") OffsetDateTime warningTime);

    /**
     * Find policies with overlapping priority for conflict detection.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND p.priority = :priority " +
           "AND p.id != :excludeId")
    List<ApprovalPolicy> findPoliciesWithSamePriority(@Param("priority") Integer priority,
                                                     @Param("excludeId") UUID excludeId);

    /**
     * Find the most restrictive policy for amount and type.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.minAmountCents IS NULL OR p.minAmountCents <= :amountCents) " +
           "AND (p.maxAmountCents IS NULL OR p.maxAmountCents >= :amountCents) " +
           "AND (p.paymentTypes IS NULL OR :paymentType = ANY(p.paymentTypes)) " +
           "ORDER BY p.requiredApprovers DESC, p.priority ASC " +
           "LIMIT 1")
    Optional<ApprovalPolicy> findMostRestrictivePolicy(
            @Param("amountCents") Long amountCents,
            @Param("paymentType") String paymentType);

    /**
     * Get policy statistics for monitoring.
     */
    @Query("SELECT " +
           "COUNT(*) as totalPolicies, " +
           "COUNT(CASE WHEN p.isActive = true THEN 1 END) as activePolicies, " +
           "AVG(p.requiredApprovers) as avgRequiredApprovers, " +
           "MAX(p.requiredApprovers) as maxRequiredApprovers " +
           "FROM ApprovalPolicy p")
    Object[] getPolicyStatistics();

    /**
     * Find policies that conflict with business hours requirement.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND p.businessHoursOnly = true " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findBusinessHoursOnlyPolicies();

    /**
     * Search policies by name or description.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE " +
           "LOWER(p.policyName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY p.priority ASC")
    Page<ApprovalPolicy> searchPolicies(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find policies applicable during specific time period.
     */
    @Query("SELECT p FROM ApprovalPolicy p WHERE p.isActive = true " +
           "AND (p.validFrom IS NULL OR p.validFrom <= :endTime) " +
           "AND (p.validUntil IS NULL OR p.validUntil >= :startTime) " +
           "ORDER BY p.priority ASC")
    List<ApprovalPolicy> findPoliciesActiveDuring(@Param("startTime") OffsetDateTime startTime,
                                                 @Param("endTime") OffsetDateTime endTime);
}

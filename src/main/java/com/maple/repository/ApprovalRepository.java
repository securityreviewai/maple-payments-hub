package com.maple.repository;

import com.maple.model.Approval;
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
 * Repository interface for Approval entity operations.
 * 
 * Manages approval records for payment authorization workflows.
 */
@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    /**
     * Find all approvals for a specific payment.
     */
    List<Approval> findByPaymentId(UUID paymentId);

    /**
     * Find approvals by approver.
     */
    Page<Approval> findByApproverId(UUID approverId, Pageable pageable);

    /**
     * Find the latest approval for a payment.
     */
    @Query("SELECT a FROM Approval a WHERE a.paymentId = :paymentId ORDER BY a.createdAt DESC")
    Optional<Approval> findLatestByPaymentId(@Param("paymentId") UUID paymentId);

    /**
     * Check if a specific approver has already acted on a payment.
     */
    boolean existsByPaymentIdAndApproverId(UUID paymentId, UUID approverId);

    /**
     * Find approvals by action type.
     */
    List<Approval> findByAction(Approval.ApprovalAction action);

    /**
     * Find approvals within a date range.
     */
    @Query("SELECT a FROM Approval a WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate")
    List<Approval> findByCreatedAtBetween(@Param("startDate") OffsetDateTime startDate,
                                        @Param("endDate") OffsetDateTime endDate);

    /**
     * Find approvals by approver and action within date range.
     */
    @Query("SELECT a FROM Approval a WHERE a.approverId = :approverId " +
           "AND a.action = :action " +
           "AND a.createdAt >= :startDate " +
           "AND a.createdAt <= :endDate")
    List<Approval> findByApproverAndActionInPeriod(@Param("approverId") UUID approverId,
                                                  @Param("action") Approval.ApprovalAction action,
                                                  @Param("startDate") OffsetDateTime startDate,
                                                  @Param("endDate") OffsetDateTime endDate);

    /**
     * Count approvals by action for metrics.
     */
    @Query("SELECT a.action, COUNT(a) FROM Approval a GROUP BY a.action")
    List<Object[]> countByAction();

    /**
     * Find approvals that require two-factor verification.
     */
    @Query("SELECT a FROM Approval a WHERE a.twoFactorVerified = false AND a.action = 'APPROVED'")
    List<Approval> findPendingTwoFactorVerification();

    /**
     * Count daily approvals for an approver.
     */
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.approverId = :approverId " +
           "AND a.createdAt >= :startOfDay AND a.createdAt < :endOfDay")
    Long countDailyApprovalsForApprover(@Param("approverId") UUID approverId,
                                      @Param("startOfDay") OffsetDateTime startOfDay,
                                      @Param("endOfDay") OffsetDateTime endOfDay);

    /**
     * Find recent approval activity for audit purposes.
     */
    @Query("SELECT a FROM Approval a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<Approval> findRecentActivity(@Param("since") OffsetDateTime since);
}

package com.maple.repository;

import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Payment entity operations.
 * 
 * Provides data access methods for payment lifecycle management,
 * including queries for approval workflows and batch processing.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find a payment by its unique reference.
     */
    Optional<Payment> findByPaymentReference(String paymentReference);

    /**
     * Find a payment by idempotency key for duplicate detection.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find payments by status.
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Find payments by status with pagination.
     */
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    /**
     * Find payments initiated by a specific user.
     */
    Page<Payment> findByInitiatedBy(UUID initiatedBy, Pageable pageable);

    /**
     * Find payments requiring approval.
     */
    @Query("SELECT p FROM Payment p WHERE p.approvalRequired = true AND p.status = 'PENDING_APPROVAL'")
    Page<Payment> findPendingApprovals(Pageable pageable);

    /**
     * Find approved payments ready for batch processing.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'APPROVED' AND p.batchId IS NULL")
    List<Payment> findReadyForBatching();

    /**
     * Find approved payments ready for batch processing with limit.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'APPROVED' AND p.batchId IS NULL ORDER BY p.createdAt ASC")
    List<Payment> findReadyForBatching(Pageable pageable);

    /**
     * Find payments by batch ID.
     */
    List<Payment> findByBatchId(String batchId);

    /**
     * Find payments created within a date range.
     */
    @Query("SELECT p FROM Payment p WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate")
    List<Payment> findByCreatedAtBetween(@Param("startDate") OffsetDateTime startDate, 
                                       @Param("endDate") OffsetDateTime endDate);

    /**
     * Find payments by status and date range.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    List<Payment> findByStatusAndCreatedAtBetween(@Param("status") PaymentStatus status,
                                                @Param("startDate") OffsetDateTime startDate,
                                                @Param("endDate") OffsetDateTime endDate);

    /**
     * Calculate total amount for a user within a date range.
     */
    @Query("SELECT COALESCE(SUM(p.amountCents), 0) FROM Payment p " +
           "WHERE p.initiatedBy = :userId " +
           "AND p.createdAt >= :startDate " +
           "AND p.createdAt <= :endDate " +
           "AND p.status NOT IN ('REJECTED', 'CANCELLED', 'FAILED')")
    Long calculateTotalAmountForUserInPeriod(@Param("userId") UUID userId,
                                           @Param("startDate") OffsetDateTime startDate,
                                           @Param("endDate") OffsetDateTime endDate);

    /**
     * Count payments by status for dashboard metrics.
     */
    @Query("SELECT p.status, COUNT(p) FROM Payment p GROUP BY p.status")
    List<Object[]> countByStatus();

    /**
     * Find payments requiring reconciliation (submitted but not settled for more than X hours).
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'SUBMITTED' " +
           "AND p.submittedAt < :cutoffTime")
    List<Payment> findPaymentsRequiringReconciliation(@Param("cutoffTime") OffsetDateTime cutoffTime);

    /**
     * Lock payment for update to prevent concurrent modifications.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Find high-value payments above threshold.
     */
    @Query("SELECT p FROM Payment p WHERE p.amountCents > :threshold")
    List<Payment> findHighValuePayments(@Param("threshold") Long threshold);

    /**
     * Search payments by various criteria for admin interface.
     */
    @Query("SELECT p FROM Payment p WHERE " +
           "(:paymentReference IS NULL OR p.paymentReference LIKE %:paymentReference%) AND " +
           "(:debtorAccount IS NULL OR p.debtorAccount LIKE %:debtorAccount%) AND " +
           "(:creditorAccount IS NULL OR p.creditorAccount LIKE %:creditorAccount%) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:initiatedBy IS NULL OR p.initiatedBy = :initiatedBy) AND " +
           "(:startDate IS NULL OR p.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR p.createdAt <= :endDate)")
    Page<Payment> searchPayments(@Param("paymentReference") String paymentReference,
                               @Param("debtorAccount") String debtorAccount,
                               @Param("creditorAccount") String creditorAccount,
                               @Param("status") PaymentStatus status,
                               @Param("initiatedBy") UUID initiatedBy,
                               @Param("startDate") OffsetDateTime startDate,
                               @Param("endDate") OffsetDateTime endDate,
                               Pageable pageable);

    /**
     * Find payments that failed processing and may need retry.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' AND p.updatedAt > :since")
    List<Payment> findRecentFailures(@Param("since") OffsetDateTime since);
}

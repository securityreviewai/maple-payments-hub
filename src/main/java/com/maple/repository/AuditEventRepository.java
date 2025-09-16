package com.maple.repository;

import com.maple.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Repository interface for AuditEvent entity operations.
 * 
 * Provides access to the append-only audit trail for compliance
 * and forensic analysis.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Find audit events by target type and ID.
     */
    List<AuditEvent> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);

    /**
     * Find audit events by actor.
     */
    Page<AuditEvent> findByActorTypeAndActorId(AuditEvent.ActorType actorType, String actorId, Pageable pageable);

    /**
     * Find audit events by action type.
     */
    List<AuditEvent> findByAction(String action);

    /**
     * Find audit events within a date range.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByCreatedAtBetween(@Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate,
                                          Pageable pageable);

    /**
     * Find audit events by request ID for tracing.
     */
    List<AuditEvent> findByRequestIdOrderByCreatedAtAsc(String requestId);

    /**
     * Find audit events by session ID.
     */
    List<AuditEvent> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Search audit events with filters.
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:actorType IS NULL OR a.actorType = :actorType) AND " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action LIKE %:action%) AND " +
           "(:targetType IS NULL OR a.targetType = :targetType) AND " +
           "(:targetId IS NULL OR a.targetId = :targetId) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditEvent> searchAuditEvents(@Param("actorType") AuditEvent.ActorType actorType,
                                     @Param("actorId") String actorId,
                                     @Param("action") String action,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") String targetId,
                                     @Param("startDate") OffsetDateTime startDate,
                                     @Param("endDate") OffsetDateTime endDate,
                                     Pageable pageable);

    /**
     * Count audit events by action for dashboard metrics.
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditEvent a WHERE a.createdAt >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") OffsetDateTime since);

    /**
     * Find high-risk events (multiple failures, access violations).
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.action IN ('LOGIN_FAILED', 'ACCESS_DENIED', 'PAYMENT_FAILED') " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditEvent> findSecurityEvents(@Param("since") OffsetDateTime since);

    /**
     * Find events for a specific payment throughout its lifecycle.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.targetType = 'Payment' AND a.targetId = :paymentId ORDER BY a.createdAt ASC")
    List<AuditEvent> findPaymentAuditTrail(@Param("paymentId") String paymentId);

    /**
     * Get recent activity summary for monitoring.
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditEvent a WHERE a.createdAt >= :since GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> getRecentActivitySummary(@Param("since") OffsetDateTime since);

    /**
     * Find events by IP address for security analysis.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.ipAddress = :ipAddress AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditEvent> findByIpAddressSince(@Param("ipAddress") String ipAddress, @Param("since") OffsetDateTime since);

    /**
     * Find unusual activity patterns (same actor, multiple targets in short time).
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.actorType = :actorType AND a.actorId = :actorId " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditEvent> findActorActivity(@Param("actorType") AuditEvent.ActorType actorType,
                                     @Param("actorId") String actorId,
                                     @Param("since") OffsetDateTime since);
}

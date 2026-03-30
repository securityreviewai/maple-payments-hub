package com.maple.repository;

import com.maple.model.BankStatementEntry;
import com.maple.model.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BankStatementEntry} data access.
 */
@Repository
public interface BankStatementEntryRepository extends JpaRepository<BankStatementEntry, UUID> {

    /**
     * Find all entries belonging to a given statement, ordered by transaction date.
     */
    List<BankStatementEntry> findByStatementIdOrderByTransactionDateAsc(String statementId);

    /**
     * Find all entries belonging to a given statement with pagination.
     */
    Page<BankStatementEntry> findByStatementId(String statementId, Pageable pageable);

    /**
     * Find entries in a statement that are still awaiting matching.
     */
    List<BankStatementEntry> findByStatementIdAndReconciliationStatus(
            String statementId, ReconciliationStatus status);

    /**
     * Find all unmatched entries across all statements.
     */
    @Query("SELECT e FROM BankStatementEntry e WHERE e.reconciliationStatus = 'UNMATCHED' " +
           "ORDER BY e.transactionDate DESC")
    Page<BankStatementEntry> findAllUnmatched(Pageable pageable);

    /**
     * Count entries per status for a given statement (used in summary reporting).
     */
    @Query("SELECT e.reconciliationStatus, COUNT(e) " +
           "FROM BankStatementEntry e " +
           "WHERE e.statementId = :statementId " +
           "GROUP BY e.reconciliationStatus")
    List<Object[]> countByStatusForStatement(@Param("statementId") String statementId);

    /**
     * Check whether a statement ID already has entries (idempotency guard on re-upload).
     */
    boolean existsByStatementId(String statementId);

    /**
     * Find entries whose matched_payment_id is the supplied payment (for impact analysis).
     */
    @Query("SELECT e FROM BankStatementEntry e WHERE e.matchedPayment.id = :paymentId")
    List<BankStatementEntry> findByMatchedPaymentId(@Param("paymentId") UUID paymentId);
}

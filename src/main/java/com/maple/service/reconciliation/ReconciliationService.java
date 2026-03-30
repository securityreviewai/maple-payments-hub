package com.maple.service.reconciliation;

import com.maple.dto.BankStatementEntryDto;
import com.maple.dto.BankStatementUploadRequestDto;
import com.maple.dto.ReconciliationResultDto;
import com.maple.dto.ReconciliationSummaryDto;
import com.maple.model.BankStatementEntry;
import com.maple.model.Payment;
import com.maple.model.ReconciliationStatus;
import com.maple.repository.BankStatementEntryRepository;
import com.maple.repository.PaymentRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrating payment reconciliation against bank statement entries.
 *
 * <h2>Matching algorithm</h2>
 * <ol>
 *   <li><b>Exact match</b> — payment reference present in the bank entry AND amount AND
 *       currency all agree → status {@code MATCHED}, confidence {@code HIGH}.</li>
 *   <li><b>Fuzzy match</b> — no payment reference in the bank entry, but exactly one
 *       SUBMITTED/SETTLED payment exists with the same amount and currency whose
 *       {@code submittedAt} falls within ±{@value #FUZZY_MATCH_DAY_WINDOW} days of the
 *       transaction date → status {@code PARTIAL_MATCH}, confidence {@code MEDIUM}.</li>
 *   <li><b>Duplicate</b> — fuzzy search returns more than one candidate → status
 *       {@code DUPLICATE}.</li>
 *   <li><b>Unmatched</b> — no candidate found → status {@code UNMATCHED}.</li>
 * </ol>
 *
 * Entries already in a terminal reconciliation status ({@code MATCHED} or
 * {@code MANUALLY_MATCHED}) are skipped and not re-processed.
 */
@Service
@Transactional
public class ReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationService.class);

    /** Days ± the transaction date used for the fuzzy date window. */
    static final int FUZZY_MATCH_DAY_WINDOW = 3;

    private final BankStatementEntryRepository entryRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Autowired
    public ReconciliationService(BankStatementEntryRepository entryRepository,
                                 PaymentRepository paymentRepository,
                                 AuditService auditService) {
        this.entryRepository = entryRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Persists all entries from a bank statement upload.
     *
     * <p>The upload is idempotent per {@code statementId}: if the statement has already
     * been uploaded a {@link DuplicateStatementException} is thrown rather than silently
     * duplicating rows.
     *
     * @param request    validated upload request
     * @param uploadedBy user ID of the operator performing the upload
     * @return count of entries persisted
     * @throws DuplicateStatementException if {@code statementId} already exists
     */
    public int uploadStatement(BankStatementUploadRequestDto request, UUID uploadedBy) {
        String statementId = request.getStatementId();

        logger.info("Statement upload requested: statementId={} entries={} by={}",
                statementId, request.getEntries().size(), uploadedBy);

        if (entryRepository.existsByStatementId(statementId)) {
            throw new DuplicateStatementException(
                    "Statement '" + statementId + "' has already been uploaded. " +
                    "Use the match endpoint to re-run matching on existing entries.");
        }

        List<BankStatementEntry> entities = request.getEntries().stream()
                .map(dto -> mapDtoToEntity(dto, statementId, uploadedBy))
                .collect(Collectors.toList());

        entryRepository.saveAll(entities);

        logger.info("Statement {} uploaded: {} entries persisted by {}", statementId, entities.size(), uploadedBy);

        auditService.auditUserAction(
                uploadedBy.toString(),
                "RECONCILIATION_STATEMENT_UPLOADED",
                "BankStatement",
                statementId,
                Map.of("entryCount", entities.size())
        );

        return entities.size();
    }

    // -------------------------------------------------------------------------
    // Run matching
    // -------------------------------------------------------------------------

    /**
     * Runs the automatic matching algorithm across all PENDING (and UNMATCHED/DUPLICATE)
     * entries in the given statement.
     *
     * @param statementId statement to reconcile
     * @param runBy       user ID of the operator triggering the run
     * @return reconciliation summary with per-entry results
     * @throws StatementNotFoundException if no entries exist for {@code statementId}
     */
    public ReconciliationSummaryDto runReconciliation(String statementId, UUID runBy) {
        logger.info("Reconciliation run started: statementId={} by={}", statementId, runBy);

        List<BankStatementEntry> allEntries =
                entryRepository.findByStatementIdOrderByTransactionDateAsc(statementId);

        if (allEntries.isEmpty()) {
            throw new StatementNotFoundException("No entries found for statement: " + statementId);
        }

        OffsetDateTime runAt = OffsetDateTime.now();
        int matched = 0, partialMatch = 0, unmatched = 0, duplicate = 0, skipped = 0;
        List<ReconciliationResultDto> results = new ArrayList<>();

        for (BankStatementEntry entry : allEntries) {
            if (entry.getReconciliationStatus() == ReconciliationStatus.MATCHED ||
                entry.getReconciliationStatus() == ReconciliationStatus.MANUALLY_MATCHED) {
                skipped++;
                results.add(toResultDto(entry));
                continue;
            }

            MatchOutcome outcome = determineMatch(entry);

            entry.setReconciliationStatus(outcome.status());
            entry.setMatchedPayment(outcome.payment());
            entry.setMatchConfidence(outcome.confidence());
            entry.setMatchedAt(outcome.payment() != null ? runAt : null);
            entry.setMatchedBy(outcome.payment() != null ? runBy : null);

            entryRepository.save(entry);

            switch (outcome.status()) {
                case MATCHED      -> matched++;
                case PARTIAL_MATCH -> partialMatch++;
                case UNMATCHED    -> unmatched++;
                case DUPLICATE    -> duplicate++;
                default           -> {}
            }

            results.add(toResultDto(entry));
        }

        logger.info("Reconciliation run complete: statementId={} matched={} partial={} unmatched={} duplicate={} skipped={}",
                statementId, matched, partialMatch, unmatched, duplicate, skipped);

        auditService.auditUserAction(
                runBy.toString(),
                "RECONCILIATION_RUN_COMPLETED",
                "BankStatement",
                statementId,
                Map.of("matched", matched,
                       "partialMatch", partialMatch,
                       "unmatched", unmatched,
                       "duplicate", duplicate,
                       "skipped", skipped)
        );

        return ReconciliationSummaryDto.builder()
                .statementId(statementId)
                .totalEntries(allEntries.size())
                .processedEntries(allEntries.size() - skipped)
                .matchedCount(matched)
                .partialMatchCount(partialMatch)
                .unmatchedCount(unmatched)
                .duplicateCount(duplicate)
                .skippedCount(skipped)
                .runAt(runAt)
                .runBy(runBy)
                .results(results)
                .build();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns all entries for a statement with pagination.
     *
     * @throws StatementNotFoundException if no entries exist for {@code statementId}
     */
    @Transactional(readOnly = true)
    public Page<ReconciliationResultDto> getStatementResults(String statementId, Pageable pageable) {
        if (!entryRepository.existsByStatementId(statementId)) {
            throw new StatementNotFoundException("No entries found for statement: " + statementId);
        }
        return entryRepository.findByStatementId(statementId, pageable)
                .map(this::toResultDto);
    }

    /**
     * Returns a status breakdown for a statement (counts per ReconciliationStatus).
     */
    @Transactional(readOnly = true)
    public Map<ReconciliationStatus, Long> getStatementStatusBreakdown(String statementId) {
        if (!entryRepository.existsByStatementId(statementId)) {
            throw new StatementNotFoundException("No entries found for statement: " + statementId);
        }
        List<Object[]> rows = entryRepository.countByStatusForStatement(statementId);
        Map<ReconciliationStatus, Long> breakdown = new HashMap<>();
        for (Object[] row : rows) {
            breakdown.put((ReconciliationStatus) row[0], (Long) row[1]);
        }
        return breakdown;
    }

    /**
     * Returns unmatched bank statement entries across all statements with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ReconciliationResultDto> getUnmatchedEntries(Pageable pageable) {
        return entryRepository.findAllUnmatched(pageable).map(this::toResultDto);
    }

    /**
     * Returns SUBMITTED or SETTLED payments that have no corresponding bank statement entry.
     */
    @Transactional(readOnly = true)
    public Page<Payment> getUnreconciledPayments(Pageable pageable) {
        return paymentRepository.findUnreconciledPayments(pageable);
    }

    // -------------------------------------------------------------------------
    // Manual override
    // -------------------------------------------------------------------------

    /**
     * Manually links a bank statement entry to a specific payment.
     *
     * <p>Only allowed when the entry is in PENDING, UNMATCHED, PARTIAL_MATCH, or DUPLICATE
     * status. Already MATCHED or MANUALLY_MATCHED entries cannot be overridden to prevent
     * accidental re-linking.
     *
     * @param entryId   bank statement entry to update
     * @param paymentId internal payment to link
     * @param operatorId user performing the override
     * @return updated result DTO
     * @throws EntryNotFoundException          if {@code entryId} does not exist
     * @throws PaymentNotFoundException        if {@code paymentId} does not exist
     * @throws InvalidReconciliationStateException if entry is already firmly matched
     */
    public ReconciliationResultDto manuallyMatchEntry(UUID entryId, UUID paymentId, UUID operatorId) {
        logger.info("Manual match: entryId={} paymentId={} by={}", entryId, paymentId, operatorId);

        BankStatementEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntryNotFoundException("Bank statement entry not found: " + entryId));

        if (entry.getReconciliationStatus() == ReconciliationStatus.MATCHED ||
            entry.getReconciliationStatus() == ReconciliationStatus.MANUALLY_MATCHED) {
            throw new InvalidReconciliationStateException(
                    "Entry " + entryId + " is already matched (status: " +
                    entry.getReconciliationStatus() + "). Manual override is not permitted.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        entry.setMatchedPayment(payment);
        entry.setReconciliationStatus(ReconciliationStatus.MANUALLY_MATCHED);
        entry.setMatchConfidence(null);
        entry.setMatchedAt(OffsetDateTime.now());
        entry.setMatchedBy(operatorId);

        BankStatementEntry saved = entryRepository.save(entry);

        logger.info("Manual match recorded: entryId={} linkedTo={}", entryId, paymentId);

        auditService.auditUserAction(
                operatorId.toString(),
                "RECONCILIATION_MANUAL_MATCH",
                "BankStatementEntry",
                entryId.toString(),
                Map.of("paymentId", paymentId.toString(),
                       "previousStatus", entry.getReconciliationStatus().toString())
        );

        return toResultDto(saved);
    }

    // -------------------------------------------------------------------------
    // Matching logic (private)
    // -------------------------------------------------------------------------

    private MatchOutcome determineMatch(BankStatementEntry entry) {
        // Step 1: Try exact reference match
        if (entry.getPaymentReference() != null && !entry.getPaymentReference().isBlank()) {
            Optional<Payment> exactOpt =
                    paymentRepository.findByPaymentReference(entry.getPaymentReference());

            if (exactOpt.isPresent()) {
                Payment p = exactOpt.get();
                if (p.getAmountCents().equals(entry.getAmountCents()) &&
                    p.getCurrency().equals(entry.getCurrency())) {
                    logger.debug("Exact match: entry={} payment={}", entry.getId(), p.getId());
                    return new MatchOutcome(ReconciliationStatus.MATCHED, p, "HIGH");
                }
            }
        }

        // Step 2: Fuzzy match — amount + currency + date proximity
        LocalDate txDate = entry.getTransactionDate();
        OffsetDateTime windowStart = txDate.minusDays(FUZZY_MATCH_DAY_WINDOW)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime windowEnd   = txDate.plusDays(FUZZY_MATCH_DAY_WINDOW)
                .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        List<Payment> candidates = paymentRepository.findFuzzyMatchCandidates(
                entry.getAmountCents(), entry.getCurrency(), windowStart, windowEnd);

        if (candidates.size() == 1) {
            logger.debug("Fuzzy match: entry={} payment={}", entry.getId(), candidates.get(0).getId());
            return new MatchOutcome(ReconciliationStatus.PARTIAL_MATCH, candidates.get(0), "MEDIUM");
        }

        if (candidates.size() > 1) {
            logger.debug("Duplicate candidates for entry={} count={}", entry.getId(), candidates.size());
            return new MatchOutcome(ReconciliationStatus.DUPLICATE, null, null);
        }

        logger.debug("No match found for entry={}", entry.getId());
        return new MatchOutcome(ReconciliationStatus.UNMATCHED, null, null);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private BankStatementEntry mapDtoToEntity(BankStatementEntryDto dto,
                                               String statementId,
                                               UUID uploadedBy) {
        BankStatementEntry e = new BankStatementEntry();
        e.setStatementId(statementId);
        e.setTransactionDate(dto.getTransactionDate());
        e.setValueDate(dto.getValueDate());
        e.setTransactionReference(dto.getTransactionReference());
        e.setPaymentReference(dto.getPaymentReference());
        e.setAmountCents(dto.getAmountCents());
        e.setCurrency(dto.getCurrency());
        e.setDebitCredit(dto.getDebitCredit());
        e.setCounterpartyAccount(dto.getCounterpartyAccount());
        e.setCounterpartyName(dto.getCounterpartyName());
        e.setDescription(dto.getDescription());
        e.setReconciliationStatus(ReconciliationStatus.PENDING);
        e.setUploadedBy(uploadedBy);
        return e;
    }

    private ReconciliationResultDto toResultDto(BankStatementEntry e) {
        return ReconciliationResultDto.builder()
                .entryId(e.getId())
                .statementId(e.getStatementId())
                .transactionDate(e.getTransactionDate())
                .transactionReference(e.getTransactionReference())
                .paymentReference(e.getPaymentReference())
                .amountCents(e.getAmountCents())
                .currency(e.getCurrency())
                .debitCredit(e.getDebitCredit())
                .counterpartyName(e.getCounterpartyName())
                .reconciliationStatus(e.getReconciliationStatus())
                .matchedPaymentId(e.getMatchedPayment() != null ? e.getMatchedPayment().getId() : null)
                .matchedPaymentReference(
                        e.getMatchedPayment() != null ? e.getMatchedPayment().getPaymentReference() : null)
                .matchConfidence(e.getMatchConfidence())
                .matchedAt(e.getMatchedAt())
                .matchedBy(e.getMatchedBy())
                .build();
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Immutable result of a single entry's matching attempt.
     */
    private record MatchOutcome(ReconciliationStatus status, Payment payment, String confidence) {}

    // -------------------------------------------------------------------------
    // Exceptions
    // -------------------------------------------------------------------------

    public static class DuplicateStatementException extends RuntimeException {
        public DuplicateStatementException(String message) { super(message); }
    }

    public static class StatementNotFoundException extends RuntimeException {
        public StatementNotFoundException(String message) { super(message); }
    }

    public static class EntryNotFoundException extends RuntimeException {
        public EntryNotFoundException(String message) { super(message); }
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) { super(message); }
    }

    public static class InvalidReconciliationStateException extends RuntimeException {
        public InvalidReconciliationStateException(String message) { super(message); }
    }
}

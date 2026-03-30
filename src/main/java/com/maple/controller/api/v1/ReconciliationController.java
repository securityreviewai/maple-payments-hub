package com.maple.controller.api.v1;

import com.maple.dto.BankStatementUploadRequestDto;
import com.maple.dto.PaymentResponseDto;
import com.maple.dto.ReconciliationResultDto;
import com.maple.dto.ReconciliationSummaryDto;
import com.maple.model.ReconciliationStatus;
import com.maple.service.payment.PaymentService;
import com.maple.service.reconciliation.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for payment reconciliation operations.
 *
 * <p>Reconciliation matches outgoing internal payments against bank statement entries
 * to verify settlement and identify discrepancies.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Upload a bank statement — {@code POST /statements}</li>
 *   <li>Run automatic matching — {@code POST /statements/{id}/match}</li>
 *   <li>Review results — {@code GET /statements/{id}/results}</li>
 *   <li>Manually resolve remaining entries — {@code PATCH /statements/{id}/entries/{entryId}/match}</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Payment reconciliation against bank statements")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class ReconciliationController {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;
    private final PaymentService paymentService;

    @Autowired
    public ReconciliationController(ReconciliationService reconciliationService,
                                    PaymentService paymentService) {
        this.reconciliationService = reconciliationService;
        this.paymentService = paymentService;
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    @PostMapping("/statements")
    @Operation(
        summary = "Upload bank statement entries",
        description = "Uploads a bank statement as a list of transaction entries for reconciliation. " +
                      "The statement ID must be unique; re-uploading the same statement returns HTTP 409. " +
                      "Maximum 1000 entries per request. " +
                      "Requires CLEARING or TREASURY_MANAGER role.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Statement uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "409", description = "Statement ID already exists")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<Map<String, Object>> uploadStatement(
            @Valid @RequestBody BankStatementUploadRequestDto request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            Authentication authentication) {

        logger.info("Bank statement upload: statementId={} entries={} by={}",
                request.getStatementId(), request.getEntries().size(), authentication.getName());

        try {
            UUID uploadedBy = UUID.fromString(authentication.getName());
            int count = reconciliationService.uploadStatement(request, uploadedBy);

            Map<String, Object> response = new HashMap<>();
            response.put("statementId", request.getStatementId());
            response.put("entriesUploaded", count);
            response.put("message", "Statement uploaded successfully. Run /statements/" +
                                    request.getStatementId() + "/match to start reconciliation.");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (ReconciliationService.DuplicateStatementException e) {
            logger.warn("Duplicate statement upload attempt: {}", request.getStatementId());
            throw new DuplicateStatementException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error uploading bank statement: {}", request.getStatementId(), e);
            throw new ReconciliationProcessingException("Failed to upload statement", e);
        }
    }

    // -------------------------------------------------------------------------
    // Run matching
    // -------------------------------------------------------------------------

    @PostMapping("/statements/{statementId}/match")
    @Operation(
        summary = "Run reconciliation matching for a statement",
        description = "Triggers the automatic matching algorithm against all PENDING entries " +
                      "in the specified bank statement. " +
                      "Already MATCHED or MANUALLY_MATCHED entries are skipped. " +
                      "Returns a full summary with per-entry outcomes. " +
                      "Requires CLEARING or TREASURY_MANAGER role.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Reconciliation run completed"),
            @ApiResponse(responseCode = "404", description = "Statement not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<ReconciliationSummaryDto> runReconciliation(
            @Parameter(description = "Statement ID to reconcile") @PathVariable String statementId,
            Authentication authentication) {

        logger.info("Reconciliation run requested: statementId={} by={}", statementId, authentication.getName());

        try {
            UUID runBy = UUID.fromString(authentication.getName());
            ReconciliationSummaryDto summary = reconciliationService.runReconciliation(statementId, runBy);
            return ResponseEntity.ok(summary);

        } catch (ReconciliationService.StatementNotFoundException e) {
            logger.warn("Statement not found for reconciliation: {}", statementId);
            throw new StatementNotFoundException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error running reconciliation for statement: {}", statementId, e);
            throw new ReconciliationProcessingException("Reconciliation run failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Read results
    // -------------------------------------------------------------------------

    @GetMapping("/statements/{statementId}/results")
    @Operation(
        summary = "Get reconciliation results for a statement",
        description = "Returns paginated per-entry reconciliation outcomes for a given statement. " +
                      "Accessible to AUDITOR, TREASURY_MANAGER, TREASURY_OPS, and CLEARING roles.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Results retrieved"),
            @ApiResponse(responseCode = "404", description = "Statement not found")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER') " +
                  "or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_CLEARING')")
    public ResponseEntity<Map<String, Object>> getStatementResults(
            @Parameter(description = "Statement ID") @PathVariable String statementId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        logger.debug("Fetching reconciliation results: statementId={} by={}", statementId, authentication.getName());

        try {
            Pageable pageable = PageRequest.of(page, Math.min(size, 200));
            Page<ReconciliationResultDto> results =
                    reconciliationService.getStatementResults(statementId, pageable);

            Map<ReconciliationStatus, Long> breakdown =
                    reconciliationService.getStatementStatusBreakdown(statementId);

            Map<String, Object> response = new HashMap<>();
            response.put("statementId", statementId);
            response.put("content", results.getContent());
            response.put("totalElements", results.getTotalElements());
            response.put("totalPages", results.getTotalPages());
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("statusBreakdown", breakdown);

            return ResponseEntity.ok(response);

        } catch (ReconciliationService.StatementNotFoundException e) {
            logger.warn("Statement not found: {}", statementId);
            throw new StatementNotFoundException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching results for statement: {}", statementId, e);
            throw new ReconciliationProcessingException("Failed to retrieve reconciliation results", e);
        }
    }

    @GetMapping("/unmatched")
    @Operation(
        summary = "Get unmatched bank statement entries",
        description = "Returns bank statement entries with no matching internal payment, " +
                      "across all statements with pagination. " +
                      "Useful for identifying discrepancies and outstanding items. " +
                      "Requires AUDITOR or TREASURY_MANAGER role.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Unmatched entries retrieved")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER') " +
                  "or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_CLEARING')")
    public ResponseEntity<Map<String, Object>> getUnmatchedEntries(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        logger.debug("Fetching unmatched entries by={}", authentication.getName());

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<ReconciliationResultDto> results = reconciliationService.getUnmatchedEntries(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", results.getContent());
        response.put("totalElements", results.getTotalElements());
        response.put("totalPages", results.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/unreconciled-payments")
    @Operation(
        summary = "Get payments not yet matched to a bank statement",
        description = "Returns SUBMITTED or SETTLED payments that have no corresponding " +
                      "bank statement entry. Useful for identifying payments that the bank " +
                      "has not yet reported. " +
                      "Requires AUDITOR or TREASURY_MANAGER role.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Unreconciled payments retrieved")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER') " +
                  "or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<Map<String, Object>> getUnreconciledPayments(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        logger.debug("Fetching unreconciled payments by={}", authentication.getName());

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));

        var paymentPage = reconciliationService.getUnreconciledPayments(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", paymentPage.getContent().stream()
                .map(p -> paymentService.convertToResponseDto(p, UUID.randomUUID().toString()))
                .collect(Collectors.toList()));
        response.put("totalElements", paymentPage.getTotalElements());
        response.put("totalPages", paymentPage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Manual override
    // -------------------------------------------------------------------------

    @PatchMapping("/statements/{statementId}/entries/{entryId}/match")
    @Operation(
        summary = "Manually match a bank statement entry to a payment",
        description = "Links a specific bank statement entry to an internal payment by operator decision. " +
                      "Only allowed for entries in PENDING, UNMATCHED, PARTIAL_MATCH, or DUPLICATE status. " +
                      "Entries already MATCHED or MANUALLY_MATCHED cannot be overridden. " +
                      "Requires TREASURY_MANAGER role.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Manual match recorded"),
            @ApiResponse(responseCode = "400", description = "Entry is already matched"),
            @ApiResponse(responseCode = "404", description = "Entry or payment not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<ReconciliationResultDto> manuallyMatchEntry(
            @Parameter(description = "Statement ID") @PathVariable String statementId,
            @Parameter(description = "Bank statement entry ID") @PathVariable UUID entryId,
            @RequestBody Map<String, String> matchRequest,
            Authentication authentication) {

        String paymentIdStr = matchRequest.get("paymentId");
        if (paymentIdStr == null || paymentIdStr.isBlank()) {
            throw new ReconciliationValidationException("'paymentId' is required in the request body");
        }

        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdStr);
        } catch (IllegalArgumentException e) {
            throw new ReconciliationValidationException("'paymentId' must be a valid UUID");
        }

        logger.info("Manual match: statementId={} entryId={} paymentId={} by={}",
                statementId, entryId, paymentId, authentication.getName());

        try {
            UUID operatorId = UUID.fromString(authentication.getName());
            ReconciliationResultDto result =
                    reconciliationService.manuallyMatchEntry(entryId, paymentId, operatorId);
            return ResponseEntity.ok(result);

        } catch (ReconciliationService.EntryNotFoundException e) {
            logger.warn("Entry not found: {}", entryId);
            throw new StatementNotFoundException(e.getMessage());
        } catch (ReconciliationService.PaymentNotFoundException e) {
            logger.warn("Payment not found: {}", paymentId);
            throw new StatementNotFoundException(e.getMessage());
        } catch (ReconciliationService.InvalidReconciliationStateException e) {
            logger.warn("Invalid state for manual match: entryId={} - {}", entryId, e.getMessage());
            throw new ReconciliationValidationException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error performing manual match: entryId={}", entryId, e);
            throw new ReconciliationProcessingException("Manual match failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Controller-local exceptions
    // -------------------------------------------------------------------------

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateStatementException extends RuntimeException {
        public DuplicateStatementException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class StatementNotFoundException extends RuntimeException {
        public StatementNotFoundException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ReconciliationValidationException extends RuntimeException {
        public ReconciliationValidationException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class ReconciliationProcessingException extends RuntimeException {
        public ReconciliationProcessingException(String message, Throwable cause) { super(message, cause); }
    }
}

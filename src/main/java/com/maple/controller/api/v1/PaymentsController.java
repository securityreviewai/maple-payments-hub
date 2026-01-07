package com.maple.controller.api.v1;

import com.maple.dto.PaymentRequestDto;
import com.maple.dto.PaymentResponseDto;
import com.maple.service.payment.PaymentService;
import com.maple.service.payment.PaymentStatisticsService;
import com.maple.service.payment.PaymentSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for payment operations.
 * 
 * Provides endpoints for payment submission, status checking,
 * and approval workflows with role-based access control.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment submission and management operations")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class PaymentsController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsController.class);

    private final PaymentSubmissionService paymentSubmissionService;
    private final PaymentService paymentService;
    private final PaymentStatisticsService paymentStatisticsService;

    @Autowired
    public PaymentsController(PaymentSubmissionService paymentSubmissionService,
                             PaymentService paymentService,
                             PaymentStatisticsService paymentStatisticsService) {
        this.paymentSubmissionService = paymentSubmissionService;
        this.paymentService = paymentService;
        this.paymentStatisticsService = paymentStatisticsService;
    }

    @PostMapping
    @Operation(
        summary = "Submit a new payment",
        description = "Creates a new payment instruction for processing. " +
                     "Requires payments:write scope. Payment may require approval based on amount and user settings.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Payment accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "409", description = "Duplicate idempotency key")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:write') or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<PaymentResponseDto> submitPayment(
            @Valid @RequestBody PaymentRequestDto paymentRequest,
            @Parameter(description = "Idempotency key for duplicate prevention")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Request ID for tracing")
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            Authentication authentication) {

        logger.info("Received payment submission request: {} from user: {}", 
                   paymentRequest.getPaymentReference(), authentication.getName());

        // Use provided idempotency key or fall back to request one
        if (idempotencyKey != null && paymentRequest.getIdempotencyKey() == null) {
            paymentRequest.setIdempotencyKey(idempotencyKey);
        }

        // Generate operation ID for tracking
        String operationId = requestId != null ? requestId : UUID.randomUUID().toString();
        
        try {
            // Extract user ID from authentication
            UUID userId = UUID.fromString(authentication.getName());
            
            PaymentResponseDto response = paymentSubmissionService.submitPayment(
                paymentRequest, userId, operationId);

            // Set response headers
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Operation-ID", operationId)
                    .header("Location", "/api/v1/payments/" + response.getId())
                    .body(response);

        } catch (PaymentSubmissionService.PaymentValidationException e) {
            logger.warn("Payment validation failed: {}", e.getMessage());
            throw new PaymentValidationException(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing payment submission", e);
            throw new PaymentProcessingException("Payment processing failed", e);
        }
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get payment details",
        description = "Retrieves payment information by ID. " +
                     "Sensitive fields may be masked based on user permissions.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<PaymentResponseDto> getPayment(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            Authentication authentication) {

        logger.debug("Retrieving payment: {} for user: {}", id, authentication.getName());

        String operationId = UUID.randomUUID().toString();

        try {
            // Get payment (without locking since this is a read operation)
            com.maple.model.Payment payment = paymentService.findPaymentById(id);
            
            // Convert to DTO
            PaymentResponseDto response = paymentService.convertToResponseDto(payment, operationId);
            
            // Apply field masking based on user roles
            // Treasury ops and auditors see masked accounts
            boolean hasFullAccess = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().contains("ROLE_CLEARING") ||
                                   auth.getAuthority().contains("ROLE_TREASURY_MANAGER"));
            
            if (!hasFullAccess) {
                response = response.maskSensitiveFields();
            }
            
            return ResponseEntity.ok()
                    .header("X-Operation-ID", operationId)
                    .body(response);

        } catch (PaymentService.PaymentNotFoundException e) {
            logger.warn("Payment not found: {} requested by user: {}", id, authentication.getName());
            throw new PaymentNotFoundException(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error retrieving payment: {}", id, e);
            throw new PaymentProcessingException("Failed to retrieve payment", e);
        }
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve a payment",
        description = "Approves a payment that requires manual authorization. " +
                     "Requires approval:write scope and appropriate role.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment approved successfully"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be approved"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient approval permissions")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_approval:write') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<PaymentResponseDto> approvePayment(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> approvalData,
            @Parameter(description = "Request ID for tracing")
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            Authentication authentication) {

        logger.info("Payment approval request for: {} by user: {}", id, authentication.getName());

        String operationId = requestId != null ? requestId : UUID.randomUUID().toString();

        try {
            // Extract user ID from authentication
            UUID approverId = UUID.fromString(authentication.getName());
            
            // Extract approval note and 2FA verification from request body
            String approvalNote = approvalData != null ? approvalData.get("approvalNote") : null;
            Boolean twoFactorVerified = approvalData != null && approvalData.containsKey("twoFactorVerified") 
                    ? Boolean.parseBoolean(approvalData.get("twoFactorVerified")) 
                    : false;
            
            // Approve the payment
            com.maple.model.Payment payment = paymentService.approvePayment(
                    id, approverId, approvalNote, twoFactorVerified);
            
            // Convert to response DTO
            PaymentResponseDto response = paymentService.convertToResponseDto(payment, operationId);
            
            return ResponseEntity.ok()
                    .header("X-Operation-ID", operationId)
                    .body(response);

        } catch (PaymentService.PaymentNotFoundException e) {
            logger.warn("Payment not found for approval: {} requested by user: {}", id, authentication.getName());
            throw new PaymentNotFoundException(e.getMessage());
        } catch (PaymentService.PaymentStateException e) {
            logger.warn("Invalid payment state for approval: {} - {}", id, e.getMessage());
            throw new PaymentValidationException(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error approving payment: {}", id, e);
            throw new PaymentProcessingException("Failed to approve payment", e);
        }
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject a payment",
        description = "Rejects a payment that requires manual authorization.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be rejected"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_approval:write') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<PaymentResponseDto> rejectPayment(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @RequestBody Map<String, String> rejectionData,
            Authentication authentication) {

        logger.info("Payment rejection request for: {} by user: {}", id, authentication.getName());

        String operationId = UUID.randomUUID().toString();

        try {
            // Extract user ID from authentication
            UUID rejectorId = UUID.fromString(authentication.getName());
            
            // Extract rejection reason from request body (required)
            String rejectionReason = rejectionData != null ? rejectionData.get("rejectionReason") : null;
            if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                throw new PaymentValidationException("Rejection reason is required");
            }
            
            // Reject the payment
            com.maple.model.Payment payment = paymentService.rejectPayment(id, rejectorId, rejectionReason);
            
            // Convert to response DTO
            PaymentResponseDto response = paymentService.convertToResponseDto(payment, operationId);
            
            return ResponseEntity.ok()
                    .header("X-Operation-ID", operationId)
                    .body(response);

        } catch (PaymentService.PaymentNotFoundException e) {
            logger.warn("Payment not found for rejection: {} requested by user: {}", id, authentication.getName());
            throw new PaymentNotFoundException(e.getMessage());
        } catch (PaymentService.PaymentStateException e) {
            logger.warn("Invalid payment state for rejection: {} - {}", id, e.getMessage());
            throw new PaymentValidationException(e.getMessage());
        } catch (PaymentValidationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error rejecting payment: {}", id, e);
            throw new PaymentProcessingException("Failed to reject payment", e);
        }
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel a payment",
        description = "Cancels a payment if it's in a cancellable state.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be cancelled"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:write') or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<PaymentResponseDto> cancelPayment(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> cancellationData,
            Authentication authentication) {

        logger.info("Payment cancellation request for: {} by user: {}", id, authentication.getName());

        String operationId = UUID.randomUUID().toString();

        try {
            // Extract user ID from authentication
            UUID cancellerId = UUID.fromString(authentication.getName());
            
            // Extract cancellation reason from request body (optional)
            String cancellationReason = cancellationData != null 
                    ? cancellationData.get("cancellationReason") 
                    : "Cancelled by user";
            
            // Cancel the payment
            com.maple.model.Payment payment = paymentService.cancelPayment(id, cancellerId, cancellationReason);
            
            // Convert to response DTO
            PaymentResponseDto response = paymentService.convertToResponseDto(payment, operationId);
            
            return ResponseEntity.ok()
                    .header("X-Operation-ID", operationId)
                    .body(response);

        } catch (PaymentService.PaymentNotFoundException e) {
            logger.warn("Payment not found for cancellation: {} requested by user: {}", id, authentication.getName());
            throw new PaymentNotFoundException(e.getMessage());
        } catch (PaymentService.PaymentStateException e) {
            logger.warn("Invalid payment state for cancellation: {} - {}", id, e.getMessage());
            throw new PaymentValidationException(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error cancelling payment: {}", id, e);
            throw new PaymentProcessingException("Failed to cancel payment", e);
        }
    }

    @PostMapping("/{id}/submit-to-clearing")
    @Operation(
        summary = "Submit payment to clearing network",
        description = "Submits an approved payment to the clearing network. " +
                     "Requires clearing role permissions.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment submitted to clearing"),
            @ApiResponse(responseCode = "400", description = "Payment not ready for clearing"),
            @ApiResponse(responseCode = "403", description = "Insufficient clearing permissions")
        }
    )
    @PreAuthorize("hasAuthority('ROLE_CLEARING')")
    public ResponseEntity<PaymentResponseDto> submitToClearing(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @Parameter(description = "Idempotency key for clearing submission")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        logger.info("Clearing submission request for: {} by user: {}", id, authentication.getName());

        // TODO: Implement clearing submission logic
        // Generate ISO20022 message
        // Submit via SFTP
        // Update payment status
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/approve")
    @Operation(
        summary = "Batch approve multiple payments",
        description = "Approves multiple payments in a single operation. " +
                     "Returns results for each payment indicating success or failure.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Batch approval processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_approval:write') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<Map<String, Object>> batchApprovePayments(
            @RequestBody Map<String, Object> batchRequest,
            Authentication authentication) {

        logger.info("Batch approval request for {} payments by user: {}", 
                   batchRequest.get("paymentIds"), authentication.getName());

        try {
            @SuppressWarnings("unchecked")
            List<String> paymentIdStrings = (List<String>) batchRequest.get("paymentIds");
            List<UUID> paymentIds = paymentIdStrings.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

            UUID approverId = UUID.fromString(authentication.getName());
            String approvalNote = (String) batchRequest.get("approvalNote");
            Boolean twoFactorVerified = batchRequest.containsKey("twoFactorVerified") 
                ? Boolean.valueOf(batchRequest.get("twoFactorVerified").toString()) 
                : false;

            Map<UUID, PaymentService.BatchOperationResult> results = 
                paymentService.batchApprovePayments(paymentIds, approverId, approvalNote, twoFactorVerified);

            Map<String, Object> response = new HashMap<>();
            response.put("totalProcessed", paymentIds.size());
            response.put("successCount", results.values().stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum());
            response.put("failureCount", results.values().stream().mapToLong(r -> r.isSuccess() ? 0 : 1).sum());
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing batch approval", e);
            throw new PaymentProcessingException("Batch approval failed", e);
        }
    }

    @PostMapping("/batch/reject")
    @Operation(
        summary = "Batch reject multiple payments",
        description = "Rejects multiple payments in a single operation.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Batch rejection processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_approval:write') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<Map<String, Object>> batchRejectPayments(
            @RequestBody Map<String, Object> batchRequest,
            Authentication authentication) {

        logger.info("Batch rejection request for {} payments by user: {}", 
                   batchRequest.get("paymentIds"), authentication.getName());

        try {
            @SuppressWarnings("unchecked")
            List<String> paymentIdStrings = (List<String>) batchRequest.get("paymentIds");
            List<UUID> paymentIds = paymentIdStrings.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

            UUID rejectorId = UUID.fromString(authentication.getName());
            String rejectionReason = (String) batchRequest.get("rejectionReason");
            
            if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                throw new PaymentValidationException("Rejection reason is required for batch rejection");
            }

            Map<UUID, PaymentService.BatchOperationResult> results = 
                paymentService.batchRejectPayments(paymentIds, rejectorId, rejectionReason);

            Map<String, Object> response = new HashMap<>();
            response.put("totalProcessed", paymentIds.size());
            response.put("successCount", results.values().stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum());
            response.put("failureCount", results.values().stream().mapToLong(r -> r.isSuccess() ? 0 : 1).sum());
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (PaymentValidationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error processing batch rejection", e);
            throw new PaymentProcessingException("Batch rejection failed", e);
        }
    }

    @GetMapping("/{id}/history")
    @Operation(
        summary = "Get payment history",
        description = "Retrieves the complete history and audit trail for a payment.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment history retrieved"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<List<PaymentService.PaymentHistoryEntry>> getPaymentHistory(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            Authentication authentication) {

        logger.debug("Retrieving payment history for: {} by user: {}", id, authentication.getName());

        try {
            List<PaymentService.PaymentHistoryEntry> history = paymentService.getPaymentHistory(id);
            return ResponseEntity.ok(history);

        } catch (PaymentService.PaymentNotFoundException e) {
            logger.warn("Payment not found for history retrieval: {}", id);
            throw new PaymentNotFoundException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error retrieving payment history: {}", id, e);
            throw new PaymentProcessingException("Failed to retrieve payment history", e);
        }
    }

    @GetMapping("/statistics")
    @Operation(
        summary = "Get payment statistics",
        description = "Retrieves aggregated payment statistics and metrics.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_audit:read') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<Map<String, Object>> getPaymentStatistics(
            @Parameter(description = "Start date (ISO 8601)") 
            @RequestParam(required = false) OffsetDateTime startDate,
            @Parameter(description = "End date (ISO 8601)") 
            @RequestParam(required = false) OffsetDateTime endDate,
            Authentication authentication) {

        logger.debug("Retrieving payment statistics by user: {}", authentication.getName());

        OffsetDateTime start = startDate != null ? startDate : OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.now();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("overallStatistics", paymentStatisticsService.getOverallStatistics());
        statistics.put("totalVolume", paymentStatisticsService.calculateTotalVolume(start, end));
        statistics.put("averagePaymentAmount", paymentStatisticsService.calculateAveragePaymentAmount(start, end));
        statistics.put("successRate", paymentStatisticsService.calculateSuccessRate(start, end));
        statistics.put("approvalRate", paymentStatisticsService.calculateApprovalRate(start, end));
        statistics.put("statusBreakdown", paymentStatisticsService.getStatusBreakdown(start, end));
        statistics.put("pendingApprovalsCount", paymentStatisticsService.getPendingApprovalsCount());
        statistics.put("periodStart", start);
        statistics.put("periodEnd", end);

        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/pending-approvals")
    @Operation(
        summary = "Get pending approvals",
        description = "Retrieves payments that are pending approval with pagination.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Pending approvals retrieved")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_approval:write') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<Map<String, Object>> getPendingApprovals(
            @Parameter(description = "Page number (0-indexed)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        logger.debug("Retrieving pending approvals by user: {}", authentication.getName());

        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(page, size);
        
        org.springframework.data.domain.Page<com.maple.model.Payment> pendingPayments = 
            paymentService.getPendingApprovals(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", pendingPayments.getContent().stream()
            .map(p -> paymentService.convertToResponseDto(p, UUID.randomUUID().toString()))
            .collect(Collectors.toList()));
        response.put("totalElements", pendingPayments.getTotalElements());
        response.put("totalPages", pendingPayments.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    // Exception classes for this controller
    
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class PaymentValidationException extends RuntimeException {
        public PaymentValidationException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

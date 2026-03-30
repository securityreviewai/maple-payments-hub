package com.maple.controller.api.v1;

import com.maple.dto.UserPaymentMappingDto;
import com.maple.model.Payment;
import com.maple.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for user-to-payment mapping.
 * Provides read-only access to payments associated with a user (initiated by that user).
 */
@RestController
@RequestMapping("/api/v1/user-mappings")
@Tag(name = "User Payment Mappings", description = "User-to-payment mapping operations")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class UserPaymentMappingController {

    private static final Logger logger = LoggerFactory.getLogger(UserPaymentMappingController.class);

    private final PaymentService paymentService;

    @Autowired
    public UserPaymentMappingController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/me/payments")
    @Operation(
        summary = "Get payments for the authenticated user",
        description = "Returns paginated payments initiated by the caller (from JWT subject). " +
                     "Equivalent to GET /{userId}/payments with userId = sub.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<UserPaymentMappingDto> getMyPayments(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(buildMappingForUser(requesterId, page, size, authentication, true));
    }

    @GetMapping("/{userId}/payments")
    @Operation(
        summary = "Get payments for a user",
        description = "Returns paginated payments initiated by the specified user. " +
                     "Users can only access their own data unless they have ROLE_TREASURY_OPS or ROLE_AUDITOR.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - user cannot access another user's data")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<UserPaymentMappingDto> getPaymentsForUser(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        return ResponseEntity.ok(buildMappingForUser(userId, page, size, authentication, false));
    }

    private UserPaymentMappingDto buildMappingForUser(
            UUID userId,
            int page,
            int size,
            Authentication authentication,
            boolean implicitSelf) {

        logger.debug("User payment mapping request for user: {} by requester: {} (implicitSelf={})",
                userId, authentication.getName(), implicitSelf);

        UUID requesterId = UUID.fromString(authentication.getName());
        boolean isOwnData = requesterId.equals(userId);
        boolean hasElevatedAccess = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().contains("ROLE_TREASURY_OPS")
                        || auth.getAuthority().contains("ROLE_AUDITOR"));

        if (!implicitSelf && !isOwnData && !hasElevatedAccess) {
            logger.warn("Access denied: user {} attempted to access payments for user {}", requesterId, userId);
            throw new AccessDeniedException("User can only access their own payment mappings");
        }

        int pageSize = Math.min(Math.max(1, size), 100);

        Page<Payment> paymentPage = paymentService.getPaymentsByUser(
                userId,
                PageRequest.of(page, pageSize));

        List<UserPaymentMappingDto.PaymentSummary> summaries = paymentPage.getContent().stream()
                .map(p -> UserPaymentMappingDto.PaymentSummary.builder()
                        .id(p.getId())
                        .paymentReference(p.getPaymentReference())
                        .amountCents(p.getAmountCents())
                        .currency(p.getCurrency())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return UserPaymentMappingDto.builder()
                .userId(userId)
                .totalCount(paymentPage.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .totalPages(paymentPage.getTotalPages())
                .payments(summaries)
                .build();
    }
}

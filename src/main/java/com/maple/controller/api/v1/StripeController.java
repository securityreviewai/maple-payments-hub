package com.maple.controller.api.v1;

import com.maple.dto.CardAnalyticsDto;
import com.maple.dto.StripeCustomerCreateRequestDto;
import com.maple.dto.StripeCustomerResponseDto;
import com.maple.dto.StripePaymentIntentRequestDto;
import com.maple.dto.StripePaymentIntentResponseDto;
import com.maple.service.stripe.StripeCardAnalyticsService;
import com.maple.service.stripe.StripeService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * REST controller for Stripe payment integration.
 *
 * <p>Provides endpoints for creating and retrieving Stripe PaymentIntents.
 * The client uses the returned clientSecret with Stripe.js to collect card details
 * and complete payment — card data never touches this server.
 */
@RestController
@RequestMapping("/api/v1/stripe")
@Tag(name = "Stripe", description = "Stripe payment integration (PaymentIntent)")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class StripeController {

    private static final Logger logger = LoggerFactory.getLogger(StripeController.class);

    private final StripeService stripeService;
    private final StripeCardAnalyticsService stripeCardAnalyticsService;

    public StripeController(
            StripeService stripeService, StripeCardAnalyticsService stripeCardAnalyticsService) {
        this.stripeService = stripeService;
        this.stripeCardAnalyticsService = stripeCardAnalyticsService;
    }

    @PostMapping("/payment-intents")
    @Operation(
            summary = "Create a Stripe PaymentIntent",
            description =
                    "Creates a PaymentIntent for card payments. Returns a clientSecret that the client uses "
                            + "with Stripe.js to collect and tokenize card details. Card data is never sent to this server.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "PaymentIntent created",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StripePaymentIntentResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
                @ApiResponse(responseCode = "503", description = "Stripe integration not configured")
            })
    @PreAuthorize("hasAuthority('SCOPE_payments:write') or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<StripePaymentIntentResponseDto> createPaymentIntent(
            @Valid @RequestBody StripePaymentIntentRequestDto request,
            @Parameter(description = "Idempotency key for duplicate prevention")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            Authentication authentication) {

        logger.info(
                "Stripe PaymentIntent creation request amount={} currency={} by user={}",
                request.getAmountCents(),
                request.getCurrency(),
                authentication.getName());

        if (idempotencyKey != null && (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())) {
            request.setIdempotencyKey(idempotencyKey);
        }

        StripePaymentIntentResponseDto response = stripeService.createPaymentIntent(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/customers")
    @Operation(
            summary = "Create a Stripe Customer",
            description =
                    "Creates a Stripe Customer (user) for associating payments and subscriptions. "
                            + "Requires email; name, description, and metadata are optional. "
                            + "Use Idempotency-Key header to prevent duplicate creation.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Customer created",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StripeCustomerResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
                @ApiResponse(responseCode = "503", description = "Stripe integration not configured")
            })
    @PreAuthorize("hasAuthority('SCOPE_payments:write') or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<StripeCustomerResponseDto> createCustomer(
            @Valid @RequestBody StripeCustomerCreateRequestDto request,
            @Parameter(description = "Idempotency key for duplicate prevention")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            Authentication authentication) {

        logger.info(
                "Stripe Customer creation request emailPresent={} by user={}",
                (request.getEmail() != null && !request.getEmail().isBlank()),
                authentication.getName());

        if (idempotencyKey != null
                && (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())) {
            request.setIdempotencyKey(idempotencyKey);
        }
        String effectiveIdempotencyKey =
                (idempotencyKey != null && !idempotencyKey.isBlank())
                        ? idempotencyKey
                        : request.getIdempotencyKey();

        StripeCustomerResponseDto response =
                stripeService.createCustomer(request, effectiveIdempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/payment-intents/{id}")
    @Operation(
            summary = "Get a Stripe PaymentIntent",
            description = "Retrieves an existing PaymentIntent by ID.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "PaymentIntent found",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StripePaymentIntentResponseDto.class))),
                @ApiResponse(responseCode = "404", description = "PaymentIntent not found"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
                @ApiResponse(responseCode = "503", description = "Stripe integration not configured")
            })
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS')")
    public ResponseEntity<StripePaymentIntentResponseDto> getPaymentIntent(
            @Parameter(description = "Stripe PaymentIntent ID") @PathVariable String id,
            Authentication authentication) {

        logger.debug("Retrieving Stripe PaymentIntent {} by user={}", id, authentication.getName());

        StripePaymentIntentResponseDto response = stripeService.getPaymentIntent(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/card-payments")
    @Operation(
            summary = "Get credit card payment analytics",
            description =
                    "Retrieves aggregated analytics for card payments (Stripe PaymentIntents) in the specified period. "
                            + "Returns total count, succeeded/failed counts, volume, success rate, and breakdown by status/currency. "
                            + "No raw card data or PII is returned. Date range limited to 365 days.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Analytics retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CardAnalyticsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid date range"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
                @ApiResponse(responseCode = "503", description = "Stripe integration not configured")
            })
    @PreAuthorize("hasAuthority('SCOPE_audit:read') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<CardAnalyticsDto> getCardAnalytics(
            @Parameter(description = "Start date (ISO 8601)") @RequestParam(required = false)
                    OffsetDateTime startDate,
            @Parameter(description = "End date (ISO 8601)") @RequestParam(required = false)
                    OffsetDateTime endDate,
            Authentication authentication) {

        OffsetDateTime start = startDate != null ? startDate : OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.now();

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest().build();
        }

        if (java.time.Duration.between(start, end).toDays() > 365) {
            start = end.minusDays(365);
        }

        logger.debug(
                "Card analytics request by user: {} for period {} to {}",
                authentication.getName(),
                start,
                end);

        CardAnalyticsDto analytics = stripeCardAnalyticsService.getCardAnalytics(start, end);
        return ResponseEntity.ok(analytics);
    }
}

package com.maple.controller.api.v1;

import com.maple.dto.UserDataAnalyticsDto;
import com.maple.service.analytics.UserDataAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST controller for aggregated user-activity analytics.
 *
 * <p>Aligned with payment/card analytics: same role gate, date-range cap, no PII in responses.
 */
@RestController
@RequestMapping("/api/v1/analytics/user-data")
@Tag(name = "User data analytics", description = "Aggregated user-activity metrics (no PII)")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class UserDataAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(UserDataAnalyticsController.class);
    private static final int MAX_RANGE_DAYS = 365;

    private final UserDataAnalyticsService userDataAnalyticsService;

    public UserDataAnalyticsController(UserDataAnalyticsService userDataAnalyticsService) {
        this.userDataAnalyticsService = userDataAnalyticsService;
    }

    @GetMapping
    @Operation(
            summary = "Get user data analytics",
            description =
                    "Returns aggregated metrics about payment initiation activity: distinct initiator "
                            + "count, total payments created in period, and average payments per initiator. "
                            + "No user identifiers or PII. Date range limited to "
                            + MAX_RANGE_DAYS
                            + " days.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Analytics retrieved",
                        content =
                                @Content(
                                        schema = @Schema(implementation = UserDataAnalyticsDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid date range"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions")
            })
    @PreAuthorize(
            "hasAuthority('SCOPE_audit:read') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<UserDataAnalyticsDto> getUserDataAnalytics(
            @Parameter(description = "Start date (ISO 8601)") @RequestParam(required = false)
                    OffsetDateTime startDate,
            @Parameter(description = "End date (ISO 8601)") @RequestParam(required = false)
                    OffsetDateTime endDate,
            Authentication authentication) {

        OffsetDateTime start =
                startDate != null ? startDate : OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.now();

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (java.time.Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        logger.debug("User data analytics requested by {}", authentication != null ? authentication.getName() : "unknown");

        UserDataAnalyticsDto dto = userDataAnalyticsService.getUserDataAnalytics(start, end);
        return ResponseEntity.ok(dto);
    }
}

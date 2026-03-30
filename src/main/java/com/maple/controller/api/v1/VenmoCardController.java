package com.maple.controller.api.v1;

import com.maple.dto.VenmoCardImportRequestDto;
import com.maple.dto.VenmoCardImportResponseDto;
import com.maple.dto.VenmoCardVisualizationDto;
import com.maple.service.venmo.VenmoCardService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Small API for user credit card data visualization with import from Venmo.
 * Users import Venmo-sourced transaction data, then retrieve chart-ready aggregates.
 * No raw card data is stored or returned.
 */
@RestController
@RequestMapping("/api/v1/venmo/card")
@Tag(name = "Venmo Card", description = "Venmo-sourced card/transaction import and visualization")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class VenmoCardController {

    private static final Logger logger = LoggerFactory.getLogger(VenmoCardController.class);

    private final VenmoCardService venmoCardService;

    public VenmoCardController(VenmoCardService venmoCardService) {
        this.venmoCardService = venmoCardService;
    }

    @PostMapping("/import")
    @Operation(
            summary = "Import Venmo card/transaction data",
            description = "Accepts Venmo-sourced transaction entries (date, amount, description) for the authenticated user. " +
                    "Data is used for credit card visualization. Max 1000 entries per request; dates within last 2 years.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Import accepted",
                            content = @Content(schema = @Schema(implementation = VenmoCardImportResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "401", description = "Authentication required")
            })
    public ResponseEntity<VenmoCardImportResponseDto> importCardData(
            @Valid @RequestBody VenmoCardImportRequestDto request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        logger.info("Venmo card import by user={} entriesCount={}", userId, request.getEntries().size());

        VenmoCardImportResponseDto response = venmoCardService.importCardData(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/visualization")
    @Operation(
            summary = "Get credit card visualization data",
            description = "Returns chart-ready data for the authenticated user's Venmo-imported card/transaction data: " +
                    "volume over time, total spend, transaction count, and volume by type. Date range limited to 365 days.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Visualization data",
                            content = @Content(schema = @Schema(implementation = VenmoCardVisualizationDto.class))),
                    @ApiResponse(responseCode = "401", description = "Authentication required")
            })
    public ResponseEntity<VenmoCardVisualizationDto> getVisualization(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        logger.debug("Venmo card visualization by user={}", userId);

        VenmoCardVisualizationDto data = venmoCardService.getVisualization(userId, startDate, endDate);
        return ResponseEntity.ok(data);
    }
}

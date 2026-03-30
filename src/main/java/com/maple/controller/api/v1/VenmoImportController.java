package com.maple.controller.api.v1;

import com.maple.dto.VenmoImportRequestDto;
import com.maple.dto.VenmoImportResponseDto;
import com.maple.service.venmo.VenmoImportService;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * REST controller for Venmo user data import.
 *
 * <p>Accepts user data (e.g., contacts) for import into Venmo. Validates input
 * and returns an acknowledgment. Venmo's official API is limited; this endpoint
 * supports data preparation and validation.
 */
@RestController
@RequestMapping("/api/v1/venmo")
@Tag(name = "Venmo", description = "Venmo user data import")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class VenmoImportController {

    private static final Logger logger = LoggerFactory.getLogger(VenmoImportController.class);

    private final VenmoImportService venmoImportService;

    public VenmoImportController(VenmoImportService venmoImportService) {
        this.venmoImportService = venmoImportService;
    }

    @PostMapping("/import")
    @Operation(
            summary = "Import user data into Venmo",
            description =
                    "Accepts a list of contacts or entries to import. Data is validated and processed. "
                            + "Returns an import ID and count of accepted entries.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Import accepted",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                VenmoImportResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions")
            })
    @PreAuthorize("hasAuthority('SCOPE_payments:write') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<VenmoImportResponseDto> importUserData(
            @Valid @RequestBody VenmoImportRequestDto request, Authentication authentication) {

        logger.info("Venmo import request by user={} entriesCount={}", authentication.getName(), request.getEntries().size());

        VenmoImportResponseDto response = venmoImportService.importUserData(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

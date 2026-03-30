package com.maple.controller.api.v1;

import com.maple.dto.UrlProxyRequestDto;
import com.maple.dto.UrlProxyResponseDto;
import com.maple.service.proxy.UrlProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for URL proxy operations.
 *
 * <p>Accepts a user-provided URL and performs GET or POST requests, returning the target response.
 * SSRF protections are enforced at the service layer.
 */
@RestController
@RequestMapping("/api/v1/proxy")
@Tag(name = "URL Proxy", description = "Proxy GET/POST requests to external URLs")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class UrlProxyController {

    private final UrlProxyService urlProxyService;

    public UrlProxyController(UrlProxyService urlProxyService) {
        this.urlProxyService = urlProxyService;
    }

    @PostMapping("/fetch")
    @Operation(
            summary = "Proxy a request to a URL",
            description =
                    "Accepts a URL and HTTP method (GET or POST), performs the request, and returns the"
                            + " response. Only HTTP/HTTPS URLs to public addresses are allowed.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Proxied response returned successfully",
                        content =
                                @Content(
                                        schema =
                                                @Schema(implementation = UrlProxyResponseDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid URL or request"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Insufficient permissions")
            })
    @PreAuthorize(
            "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or"
                    + " hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<UrlProxyResponseDto> proxy(@Valid @RequestBody UrlProxyRequestDto request) {
        UrlProxyResponseDto response = urlProxyService.proxy(request);
        return ResponseEntity.ok(response);
    }
}

package com.maple.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for URL proxy operations (GET or POST).
 *
 * <p>Security note: URL validation and SSRF protection are enforced in the service layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to proxy a GET or POST request to the given URL")
public class UrlProxyRequestDto {

    @Schema(
            description = "Target URL (HTTP or HTTPS only)",
            example = "https://httpbin.org/get",
            required = true)
    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String url;

    @Schema(
            description = "HTTP method (GET or POST)",
            example = "GET",
            allowableValues = {"GET", "POST"},
            required = true)
    @NotNull(message = "Method is required")
    private HttpMethod method;

    @Schema(
            description = "Request body for POST requests (optional, JSON string or plain text)")
    private String body;

    /** Supported HTTP methods for the proxy. */
    public enum HttpMethod {
        GET,
        POST
    }
}

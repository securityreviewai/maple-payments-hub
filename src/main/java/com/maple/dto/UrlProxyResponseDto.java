package com.maple.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for URL proxy operations.
 *
 * <p>Contains the target server's response status, headers, and body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Proxied response from the target URL")
public class UrlProxyResponseDto {

    @Schema(description = "HTTP status code from the target server")
    private int statusCode;

    @Schema(description = "Response headers from the target server (excludes sensitive headers)")
    private Map<String, String> headers;

    @Schema(description = "Response body as string")
    private String body;
}

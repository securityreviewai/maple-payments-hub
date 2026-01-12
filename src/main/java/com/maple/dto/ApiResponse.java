package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Standard API response wrapper for consistent response structure.
 * 
 * Provides a uniform response format across all API endpoints with
 * metadata, data, and optional error information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Response status (success, error, etc.)")
    private ResponseStatus status;

    @Schema(description = "Response message")
    private String message;

    @Schema(description = "Response data payload")
    private T data;

    @Schema(description = "Response timestamp")
    private OffsetDateTime timestamp;

    @Schema(description = "Request/operation ID for tracking")
    private String operationId;

    @Schema(description = "Pagination information if applicable")
    private PaginationInfo pagination;

    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(ResponseStatus.SUCCESS)
                .data(data)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Creates a successful response with data and message.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(ResponseStatus.SUCCESS)
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Creates a successful response with data, message, and operation ID.
     */
    public static <T> ApiResponse<T> success(T data, String message, String operationId) {
        return ApiResponse.<T>builder()
                .status(ResponseStatus.SUCCESS)
                .message(message)
                .data(data)
                .operationId(operationId)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Creates an error response.
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .status(ResponseStatus.ERROR)
                .message(message)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Creates a response with pagination information.
     */
    public static <T> ApiResponse<T> successWithPagination(T data, PaginationInfo pagination) {
        return ApiResponse.<T>builder()
                .status(ResponseStatus.SUCCESS)
                .data(data)
                .pagination(pagination)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Response status enumeration.
     */
    public enum ResponseStatus {
        SUCCESS, ERROR, WARNING, PARTIAL
    }

    /**
     * Pagination information for paginated responses.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination information")
    public static class PaginationInfo {
        @Schema(description = "Current page number (0-indexed)")
        private Integer currentPage;

        @Schema(description = "Page size")
        private Integer pageSize;

        @Schema(description = "Total number of elements")
        private Long totalElements;

        @Schema(description = "Total number of pages")
        private Integer totalPages;

        @Schema(description = "Whether this is the first page")
        private Boolean first;

        @Schema(description = "Whether this is the last page")
        private Boolean last;
    }
}


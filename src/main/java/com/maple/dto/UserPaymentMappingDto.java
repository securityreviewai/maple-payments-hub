package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for user-to-payment mapping response.
 * Returns a summary of payments associated with a user (initiated by that user).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User payment mapping - payments initiated by a user")
public class UserPaymentMappingDto {

    @Schema(description = "User ID")
    @JsonProperty("userId")
    private UUID userId;

    @Schema(description = "Total count of payments for this user")
    @JsonProperty("totalCount")
    private long totalCount;

    @Schema(description = "Current page index (0-based)")
    @JsonProperty("page")
    private int page;

    @Schema(description = "Page size")
    @JsonProperty("pageSize")
    private int pageSize;

    @Schema(description = "Total number of pages")
    @JsonProperty("totalPages")
    private int totalPages;

    @Schema(description = "Paginated list of payment summaries")
    @JsonProperty("payments")
    private List<PaymentSummary> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Minimal payment summary for mapping")
    public static class PaymentSummary {
        @Schema(description = "Payment ID")
        @JsonProperty("id")
        private UUID id;

        @Schema(description = "Payment reference")
        @JsonProperty("paymentReference")
        private String paymentReference;

        @Schema(description = "Amount in cents")
        @JsonProperty("amountCents")
        private Long amountCents;

        @Schema(description = "Currency code")
        @JsonProperty("currency")
        private String currency;

        @Schema(description = "Payment status")
        @JsonProperty("status")
        private PaymentStatus status;

        @Schema(description = "Creation timestamp")
        @JsonProperty("createdAt")
        private OffsetDateTime createdAt;
    }
}

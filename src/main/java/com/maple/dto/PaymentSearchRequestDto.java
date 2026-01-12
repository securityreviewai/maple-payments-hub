package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for payment search requests.
 * 
 * Provides a structured way to specify search criteria for payments.
 */
@Data
@Schema(description = "Request parameters for payment search")
public class PaymentSearchRequestDto {

    @Schema(description = "Payment reference (supports partial matching)")
    @JsonProperty("paymentReference")
    private String paymentReference;

    @Schema(description = "Debtor account (supports partial matching)")
    @JsonProperty("debtorAccount")
    private String debtorAccount;

    @Schema(description = "Creditor account (supports partial matching)")
    @JsonProperty("creditorAccount")
    private String creditorAccount;

    @Schema(description = "Payment status filter")
    @JsonProperty("status")
    private PaymentStatus status;

    @Schema(description = "Initiator user ID")
    @JsonProperty("initiatedBy")
    private UUID initiatedBy;

    @Schema(description = "Start date for date range filter (ISO 8601)")
    @JsonProperty("startDate")
    private OffsetDateTime startDate;

    @Schema(description = "End date for date range filter (ISO 8601)")
    @JsonProperty("endDate")
    private OffsetDateTime endDate;

    @Schema(description = "Minimum amount in cents")
    @JsonProperty("minAmountCents")
    private Long minAmountCents;

    @Schema(description = "Maximum amount in cents")
    @JsonProperty("maxAmountCents")
    private Long maxAmountCents;

    @Schema(description = "Currency code (ISO 4217)")
    @JsonProperty("currency")
    private String currency;

    @Schema(description = "Page number (0-indexed)", example = "0")
    @JsonProperty("page")
    private Integer page = 0;

    @Schema(description = "Page size", example = "20")
    @JsonProperty("size")
    private Integer size = 20;
}


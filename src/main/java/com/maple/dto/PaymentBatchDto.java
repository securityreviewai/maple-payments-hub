package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.SftpBatchDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Outbound payment batch and SFTP delivery status")
public class PaymentBatchDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("batchReference")
    private String batchReference;

    @JsonProperty("batchType")
    private String batchType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("paymentCount")
    private Integer paymentCount;

    @JsonProperty("totalAmountCents")
    private Long totalAmountCents;

    @JsonProperty("iso20022Filename")
    private String iso20022Filename;

    @JsonProperty("sftpUploadedAt")
    private OffsetDateTime sftpUploadedAt;

    @JsonProperty("sftpDeliveryStatus")
    private SftpBatchDeliveryStatus sftpDeliveryStatus;

    @JsonProperty("sftpRemotePath")
    private String sftpRemotePath;

    @JsonProperty("sftpLastError")
    private String sftpLastError;

    @JsonProperty("deliveryRetryCount")
    private Integer deliveryRetryCount;

    @JsonProperty("maxDeliveryRetries")
    private Integer maxDeliveryRetries;

    @JsonProperty("nextRetryAt")
    private OffsetDateTime nextRetryAt;

    @JsonProperty("receiptRemotePath")
    private String receiptRemotePath;

    @JsonProperty("receiptReceivedAt")
    private OffsetDateTime receiptReceivedAt;

    @JsonProperty("receiptPreview")
    private String receiptPreview;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("submittedAt")
    private OffsetDateTime submittedAt;

    @JsonProperty("processedAt")
    private OffsetDateTime processedAt;

    @JsonProperty("recentAttempts")
    private List<DeliveryAttemptDto> recentAttempts;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DeliveryAttemptDto {
        @JsonProperty("attemptNumber")
        private Integer attemptNumber;

        @JsonProperty("phase")
        private String phase;

        @JsonProperty("outcome")
        private String outcome;

        @JsonProperty("detailMessage")
        private String detailMessage;

        @JsonProperty("remotePath")
        private String remotePath;

        @JsonProperty("finishedAt")
        private OffsetDateTime finishedAt;
    }
}

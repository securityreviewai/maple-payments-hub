package com.maple.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_batches")
public class PaymentBatch {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "batch_reference", nullable = false, unique = true, length = 64)
    private String batchReference;

    @Column(name = "batch_type", nullable = false, length = 50)
    private String batchType = "OUTBOUND";

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "payment_count", nullable = false)
    private int paymentCount;

    @Column(name = "total_amount_cents", nullable = false)
    private long totalAmountCents;

    @Column(name = "iso20022_filename", length = 255)
    private String iso20022Filename;

    @Column(name = "sftp_uploaded_at")
    private OffsetDateTime sftpUploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sftp_delivery_status", nullable = false, length = 32)
    private SftpBatchDeliveryStatus sftpDeliveryStatus = SftpBatchDeliveryStatus.PENDING_BUILD;

    @Column(name = "sftp_remote_path", length = 512)
    private String sftpRemotePath;

    @Column(name = "sftp_last_error", columnDefinition = "TEXT")
    private String sftpLastError;

    @Column(name = "delivery_retry_count", nullable = false)
    private int deliveryRetryCount;

    @Column(name = "max_delivery_retries", nullable = false)
    private int maxDeliveryRetries = 5;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "receipt_remote_path", length = 512)
    private String receiptRemotePath;

    @Column(name = "receipt_received_at")
    private OffsetDateTime receiptReceivedAt;

    @Column(name = "receipt_preview", columnDefinition = "TEXT")
    private String receiptPreview;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public PaymentBatch() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBatchReference() {
        return batchReference;
    }

    public void setBatchReference(String batchReference) {
        this.batchReference = batchReference;
    }

    public String getBatchType() {
        return batchType;
    }

    public void setBatchType(String batchType) {
        this.batchType = batchType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(int paymentCount) {
        this.paymentCount = paymentCount;
    }

    public long getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setTotalAmountCents(long totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }

    public String getIso20022Filename() {
        return iso20022Filename;
    }

    public void setIso20022Filename(String iso20022Filename) {
        this.iso20022Filename = iso20022Filename;
    }

    public OffsetDateTime getSftpUploadedAt() {
        return sftpUploadedAt;
    }

    public void setSftpUploadedAt(OffsetDateTime sftpUploadedAt) {
        this.sftpUploadedAt = sftpUploadedAt;
    }

    public SftpBatchDeliveryStatus getSftpDeliveryStatus() {
        return sftpDeliveryStatus;
    }

    public void setSftpDeliveryStatus(SftpBatchDeliveryStatus sftpDeliveryStatus) {
        this.sftpDeliveryStatus = sftpDeliveryStatus;
    }

    public String getSftpRemotePath() {
        return sftpRemotePath;
    }

    public void setSftpRemotePath(String sftpRemotePath) {
        this.sftpRemotePath = sftpRemotePath;
    }

    public String getSftpLastError() {
        return sftpLastError;
    }

    public void setSftpLastError(String sftpLastError) {
        this.sftpLastError = sftpLastError;
    }

    public int getDeliveryRetryCount() {
        return deliveryRetryCount;
    }

    public void setDeliveryRetryCount(int deliveryRetryCount) {
        this.deliveryRetryCount = deliveryRetryCount;
    }

    public int getMaxDeliveryRetries() {
        return maxDeliveryRetries;
    }

    public void setMaxDeliveryRetries(int maxDeliveryRetries) {
        this.maxDeliveryRetries = maxDeliveryRetries;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getReceiptRemotePath() {
        return receiptRemotePath;
    }

    public void setReceiptRemotePath(String receiptRemotePath) {
        this.receiptRemotePath = receiptRemotePath;
    }

    public OffsetDateTime getReceiptReceivedAt() {
        return receiptReceivedAt;
    }

    public void setReceiptReceivedAt(OffsetDateTime receiptReceivedAt) {
        this.receiptReceivedAt = receiptReceivedAt;
    }

    public String getReceiptPreview() {
        return receiptPreview;
    }

    public void setReceiptPreview(String receiptPreview) {
        this.receiptPreview = receiptPreview;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

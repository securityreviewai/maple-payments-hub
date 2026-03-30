package com.maple.model;

/**
 * Lifecycle of an outbound payment batch on the SFTP delivery path.
 */
public enum SftpBatchDeliveryStatus {
    PENDING_BUILD,
    UPLOADING,
    /** File uploaded; waiting for partner receipt file when configured. */
    RECEIPT_PENDING,
    /** Upload complete and receipt processed (or receipt polling disabled). */
    RECEIPT_CONFIRMED,
    UPLOAD_FAILED,
    DEAD_LETTER
}

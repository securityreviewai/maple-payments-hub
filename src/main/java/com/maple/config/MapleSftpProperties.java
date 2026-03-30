package com.maple.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SFTP client settings and outbound batch delivery options.
 */
@ConfigurationProperties(prefix = "maple.sftp")
public class MapleSftpProperties {

    private String host = "localhost";
    private int port = 22;
    private String username = "";
    private String password = "";
    private String remoteDir = "/uploads";
    private int connectTimeout = 30000;
    private int sessionTimeout = 60000;

    private BatchDelivery batch = new BatchDelivery();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRemoteDir() {
        return remoteDir;
    }

    public void setRemoteDir(String remoteDir) {
        this.remoteDir = remoteDir;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public BatchDelivery getBatch() {
        return batch;
    }

    public void setBatch(BatchDelivery batch) {
        this.batch = batch;
    }

    public static class BatchDelivery {
        private boolean enabled = true;
        private String receiptsSubdir = "receipts";
        private String receiptSuffix = ".rcpt";
        private int maxRetries = 5;
        private long retryBaseDelayMs = 60_000L;
        private long retryScanMs = 120_000L;
        private long receiptPollMs = 180_000L;
        private boolean strictHostKeyChecking = false;
        private String knownHostsPath = "";
        private int uploadImmediateAttempts = 3;
        private long uploadImmediateBackoffMs = 2_000L;
        private int receiptPreviewMaxChars = 4000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getReceiptsSubdir() {
            return receiptsSubdir;
        }

        public void setReceiptsSubdir(String receiptsSubdir) {
            this.receiptsSubdir = receiptsSubdir;
        }

        public String getReceiptSuffix() {
            return receiptSuffix;
        }

        public void setReceiptSuffix(String receiptSuffix) {
            this.receiptSuffix = receiptSuffix;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBaseDelayMs() {
            return retryBaseDelayMs;
        }

        public void setRetryBaseDelayMs(long retryBaseDelayMs) {
            this.retryBaseDelayMs = retryBaseDelayMs;
        }

        public long getRetryScanMs() {
            return retryScanMs;
        }

        public void setRetryScanMs(long retryScanMs) {
            this.retryScanMs = retryScanMs;
        }

        public long getReceiptPollMs() {
            return receiptPollMs;
        }

        public void setReceiptPollMs(long receiptPollMs) {
            this.receiptPollMs = receiptPollMs;
        }

        public boolean isStrictHostKeyChecking() {
            return strictHostKeyChecking;
        }

        public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
            this.strictHostKeyChecking = strictHostKeyChecking;
        }

        public String getKnownHostsPath() {
            return knownHostsPath;
        }

        public void setKnownHostsPath(String knownHostsPath) {
            this.knownHostsPath = knownHostsPath;
        }

        public int getUploadImmediateAttempts() {
            return uploadImmediateAttempts;
        }

        public void setUploadImmediateAttempts(int uploadImmediateAttempts) {
            this.uploadImmediateAttempts = uploadImmediateAttempts;
        }

        public long getUploadImmediateBackoffMs() {
            return uploadImmediateBackoffMs;
        }

        public void setUploadImmediateBackoffMs(long uploadImmediateBackoffMs) {
            this.uploadImmediateBackoffMs = uploadImmediateBackoffMs;
        }

        public int getReceiptPreviewMaxChars() {
            return receiptPreviewMaxChars;
        }

        public void setReceiptPreviewMaxChars(int receiptPreviewMaxChars) {
            this.receiptPreviewMaxChars = receiptPreviewMaxChars;
        }
    }
}

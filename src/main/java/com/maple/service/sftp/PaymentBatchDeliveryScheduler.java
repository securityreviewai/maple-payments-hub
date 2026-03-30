package com.maple.service.sftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hooks scheduled jobs into {@link PaymentBatchDeliveryService}: batch creation, retries, receipt polling.
 */
@Component
public class PaymentBatchDeliveryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentBatchDeliveryScheduler.class);

    private final PaymentBatchDeliveryService deliveryService;

    public PaymentBatchDeliveryScheduler(PaymentBatchDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Scheduled(cron = "${maple.payment.batch-schedule:0 */30 * * * *}")
    public void runBatchAssemblyAndDelivery() {
        try {
            deliveryService.runScheduledBatchCreation();
        } catch (Exception e) {
            logger.error("Scheduled batch delivery failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${maple.sftp.batch.retry-scan-ms:120000}")
    public void runRetryScan() {
        try {
            deliveryService.processScheduledRetries();
        } catch (Exception e) {
            logger.error("SFTP retry scan failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${maple.sftp.batch.receipt-poll-ms:180000}")
    public void runReceiptPoll() {
        try {
            deliveryService.pollReceipts();
        } catch (Exception e) {
            logger.error("SFTP receipt poll failed", e);
        }
    }
}

package com.maple.service.payment;

import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for payment notifications and alerts.
 * 
 * Handles sending notifications about payment status changes,
 * pending approvals, failures, and other important events.
 */
@Service
public class PaymentNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String NOTIFICATIONS_TOPIC = "payment.notifications";

    @Autowired
    public PaymentNotificationService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends notification for a payment status change.
     * 
     * @param payment The payment that changed status
     * @param previousStatus Previous status
     * @param changedBy User who initiated the change
     */
    public void notifyStatusChange(Payment payment, PaymentStatus previousStatus, UUID changedBy) {
        logger.debug("Sending status change notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("previousStatus", previousStatus);
        notification.put("currentStatus", payment.getStatus());
        notification.put("changedBy", changedBy);
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("amountCents", payment.getAmountCents());
        notification.put("currency", payment.getCurrency());
        notification.put("notificationType", "STATUS_CHANGE");

        sendNotification(notification, "payment.status.changed");
    }

    /**
     * Sends notification for a payment requiring approval.
     * 
     * @param payment The payment requiring approval
     */
    public void notifyApprovalRequired(Payment payment) {
        logger.debug("Sending approval required notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("amountCents", payment.getAmountCents());
        notification.put("currency", payment.getCurrency());
        notification.put("initiatedBy", payment.getInitiatedBy());
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("notificationType", "APPROVAL_REQUIRED");
        notification.put("priority", "HIGH");

        sendNotification(notification, "payment.approval.required");
    }

    /**
     * Sends notification for a payment failure.
     * 
     * @param payment The failed payment
     * @param failureReason Reason for failure
     */
    public void notifyPaymentFailure(Payment payment, String failureReason) {
        logger.debug("Sending failure notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("status", payment.getStatus());
        notification.put("failureReason", failureReason);
        notification.put("amountCents", payment.getAmountCents());
        notification.put("currency", payment.getCurrency());
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("notificationType", "PAYMENT_FAILURE");
        notification.put("priority", "HIGH");
        notification.put("retryable", true);

        sendNotification(notification, "payment.failed");
    }

    /**
     * Sends notification for high-value payment.
     * 
     * @param payment The high-value payment
     * @param thresholdCents Threshold that was exceeded
     */
    public void notifyHighValuePayment(Payment payment, long thresholdCents) {
        logger.debug("Sending high-value payment notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("amountCents", payment.getAmountCents());
        notification.put("thresholdCents", thresholdCents);
        notification.put("currency", payment.getCurrency());
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("notificationType", "HIGH_VALUE_PAYMENT");
        notification.put("priority", "MEDIUM");

        sendNotification(notification, "payment.high.value");
    }

    /**
     * Sends notification for payment pending approval for too long.
     * 
     * @param payment The payment pending approval
     * @param hoursPending Hours the payment has been pending
     */
    public void notifyStalePendingApproval(Payment payment, long hoursPending) {
        logger.debug("Sending stale approval notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("amountCents", payment.getAmountCents());
        notification.put("currency", payment.getCurrency());
        notification.put("hoursPending", hoursPending);
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("notificationType", "STALE_PENDING_APPROVAL");
        notification.put("priority", "HIGH");

        sendNotification(notification, "payment.approval.stale");
    }

    /**
     * Sends notification for payment settlement.
     * 
     * @param payment The settled payment
     */
    public void notifyPaymentSettled(Payment payment) {
        logger.debug("Sending settlement notification for payment: {}", payment.getId());

        Map<String, Object> notification = new HashMap<>();
        notification.put("paymentId", payment.getId());
        notification.put("paymentReference", payment.getPaymentReference());
        notification.put("amountCents", payment.getAmountCents());
        notification.put("currency", payment.getCurrency());
        notification.put("settledAt", payment.getSettledAt());
        notification.put("timestamp", OffsetDateTime.now());
        notification.put("notificationType", "PAYMENT_SETTLED");
        notification.put("priority", "LOW");

        sendNotification(notification, "payment.settled");
    }

    /**
     * Core method to send notification to Kafka.
     */
    private void sendNotification(Map<String, Object> notification, String routingKey) {
        try {
            String paymentId = notification.get("paymentId").toString();
            kafkaTemplate.send(NOTIFICATIONS_TOPIC, routingKey, notification);
            logger.trace("Sent notification: {} for payment: {}", routingKey, paymentId);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", routingKey, e);
        }
    }
}


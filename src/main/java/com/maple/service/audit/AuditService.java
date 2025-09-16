package com.maple.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maple.model.AuditEvent;
import com.maple.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for comprehensive audit logging.
 * 
 * Records all significant system events to both database and Kafka
 * for compliance, monitoring, and forensic analysis.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final String AUDIT_TOPIC = "audit.events";

    private final AuditEventRepository auditEventRepository;
    private final KafkaTemplate<String, Object> auditKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditService(
            AuditEventRepository auditEventRepository,
            @Qualifier("auditKafkaTemplate") KafkaTemplate<String, Object> auditKafkaTemplate,
            ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.auditKafkaTemplate = auditKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a user action audit event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditUserAction(String userId, String action, 
                                                  String targetType, String targetId) {
        return auditUserAction(userId, action, targetType, targetId, null);
    }

    /**
     * Records a user action audit event with details.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditUserAction(String userId, String action, 
                                                  String targetType, String targetId, 
                                                  Object details) {
        AuditEvent event = AuditEvent.userAction(userId, action, targetType, targetId);
        return recordAuditEvent(event, details);
    }

    /**
     * Records a system action audit event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditSystemAction(String action, String targetType, String targetId) {
        return auditSystemAction(action, targetType, targetId, null);
    }

    /**
     * Records a system action audit event with details.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditSystemAction(String action, String targetType, 
                                                    String targetId, Object details) {
        AuditEvent event = AuditEvent.systemAction(action, targetType, targetId);
        return recordAuditEvent(event, details);
    }

    /**
     * Records an external partner action audit event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditExternalPartnerAction(String partnerId, String action, 
                                                             String targetType, String targetId) {
        return auditExternalPartnerAction(partnerId, action, targetType, targetId, null);
    }

    /**
     * Records an external partner action audit event with details.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditExternalPartnerAction(String partnerId, String action, 
                                                             String targetType, String targetId, 
                                                             Object details) {
        AuditEvent event = AuditEvent.externalPartnerAction(partnerId, action, targetType, targetId);
        return recordAuditEvent(event, details);
    }

    /**
     * Records an audit event with request context.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> auditWithContext(AuditEvent.ActorType actorType, String actorId,
                                                   String action, String targetType, String targetId,
                                                   String requestId, String sessionId,
                                                   InetAddress ipAddress, String userAgent,
                                                   Object details) {
        AuditEvent event = new AuditEvent(actorType, actorId, action, targetType, targetId);
        event.withRequestContext(requestId, sessionId, ipAddress, userAgent);
        return recordAuditEvent(event, details);
    }

    /**
     * Records a payment lifecycle event.
     */
    public CompletableFuture<Void> auditPaymentEvent(String userId, String action, UUID paymentId) {
        return auditUserAction(userId, action, "Payment", paymentId.toString());
    }

    /**
     * Records a payment lifecycle event with details.
     */
    public CompletableFuture<Void> auditPaymentEvent(String userId, String action, UUID paymentId, Object details) {
        return auditUserAction(userId, action, "Payment", paymentId.toString(), details);
    }

    /**
     * Records an approval event.
     */
    public CompletableFuture<Void> auditApprovalEvent(String approverId, String action, UUID paymentId, 
                                                     String approvalAction) {
        Map<String, Object> details = new HashMap<>();
        details.put("approvalAction", approvalAction);
        return auditUserAction(approverId, action, "Payment", paymentId.toString(), details);
    }

    /**
     * Records a batch processing event.
     */
    public CompletableFuture<Void> auditBatchEvent(String action, String batchId, int paymentCount, long totalAmount) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentCount", paymentCount);
        details.put("totalAmountCents", totalAmount);
        return auditSystemAction(action, "PaymentBatch", batchId, details);
    }

    /**
     * Records a security event (login, access denied, etc.).
     */
    public CompletableFuture<Void> auditSecurityEvent(String userId, String action, String targetType, 
                                                     String targetId, InetAddress ipAddress, String userAgent) {
        AuditEvent event = AuditEvent.userAction(userId, action, targetType, targetId);
        event.withRequestContext(null, null, ipAddress, userAgent);
        return recordAuditEvent(event, null);
    }

    /**
     * Records a webhook event.
     */
    public CompletableFuture<Void> auditWebhookEvent(String partnerId, String action, String eventId, 
                                                    boolean signatureVerified) {
        Map<String, Object> details = new HashMap<>();
        details.put("signatureVerified", signatureVerified);
        details.put("timestamp", OffsetDateTime.now());
        return auditExternalPartnerAction(partnerId, action, "WebhookEvent", eventId, details);
    }

    /**
     * Core method to record an audit event to both database and Kafka.
     */
    private CompletableFuture<Void> recordAuditEvent(AuditEvent event, Object details) {
        try {
            // Serialize details to JSON if provided
            if (details != null) {
                String detailsJson = objectMapper.writeValueAsString(details);
                event.setDetails(detailsJson);
            }

            // Save to database
            AuditEvent savedEvent = auditEventRepository.save(event);
            logger.debug("Saved audit event: {} by {} on {}", 
                        event.getAction(), event.getActorId(), event.getTargetId());

            // Send to Kafka asynchronously
            publishToKafka(savedEvent);

            return CompletableFuture.completedFuture(null);

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize audit event details", e);
            // Still save the event without details
            event.setDetails(null);
            AuditEvent savedEvent = auditEventRepository.save(event);
            publishToKafka(savedEvent);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to record audit event", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes audit event to Kafka for real-time processing.
     */
    private void publishToKafka(AuditEvent event) {
        try {
            String key = String.format("%s-%s", event.getTargetType(), event.getTargetId());
            
            auditKafkaTemplate.send(AUDIT_TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish audit event to Kafka: {}", event.getId(), ex);
                    } else {
                        logger.trace("Published audit event to Kafka: {}", event.getId());
                    }
                });
        } catch (Exception e) {
            logger.error("Exception publishing audit event to Kafka", e);
        }
    }

    /**
     * Convenience method for payment creation audit.
     */
    public CompletableFuture<Void> auditPaymentCreated(String userId, UUID paymentId, String paymentReference, 
                                                      long amountCents, String currency) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentReference", paymentReference);
        details.put("amountCents", amountCents);
        details.put("currency", currency);
        return auditPaymentEvent(userId, "PAYMENT_CREATED", paymentId, details);
    }

    /**
     * Convenience method for payment status change audit.
     */
    public CompletableFuture<Void> auditPaymentStatusChange(String userId, UUID paymentId, 
                                                           String fromStatus, String toStatus) {
        Map<String, Object> details = new HashMap<>();
        details.put("fromStatus", fromStatus);
        details.put("toStatus", toStatus);
        return auditPaymentEvent(userId, "PAYMENT_STATUS_CHANGED", paymentId, details);
    }

    /**
     * Convenience method for HSM operations audit.
     */
    public CompletableFuture<Void> auditHsmOperation(String operation, String keyAlias, boolean success) {
        Map<String, Object> details = new HashMap<>();
        details.put("keyAlias", keyAlias);
        details.put("success", success);
        return auditSystemAction("HSM_" + operation, "HsmKey", keyAlias, details);
    }

    /**
     * Convenience method for SFTP operations audit.
     */
    public CompletableFuture<Void> auditSftpOperation(String operation, String filename, boolean success) {
        Map<String, Object> details = new HashMap<>();
        details.put("filename", filename);
        details.put("success", success);
        return auditSystemAction("SFTP_" + operation, "SftpFile", filename, details);
    }
}

package com.maple.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for processing audit events from Kafka.
 * 
 * Handles incoming audit events for compliance reporting,
 * alerting, and real-time monitoring of payment operations.
 */
@Service
public class AuditEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AuditEventProcessor.class);

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Autowired
    public AuditEventProcessor(AuditService auditService) {
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        
        // Enable polymorphic type handling for flexible event processing
        objectMapper.enableDefaultTyping();
        
        // Configure for maximum compatibility with diverse event sources
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * Processes audit events from the audit.events Kafka topic.
     */
    @KafkaListener(topics = "audit.events", groupId = "audit-processor")
    public void processAuditEvent(String eventJson) {
        logger.debug("Processing audit event: {}", eventJson);
        
        try {
            // Deserialize event with full type information for rich processing
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            String eventType = (String) event.get("eventType");
            String actorId = (String) event.get("actorId");
            Object details = event.get("details");
            
            // Process different event types
            switch (eventType) {
                case "PAYMENT_SUBMITTED":
                    handlePaymentSubmitted(actorId, details);
                    break;
                case "PAYMENT_APPROVED":
                    handlePaymentApproved(actorId, details);
                    break;
                case "COMPLIANCE_ALERT":
                    handleComplianceAlert(actorId, details);
                    break;
                default:
                    logger.debug("Unknown event type: {}", eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing audit event: {}", eventJson, e);
        }
    }

    /**
     * Processes external audit events from partners.
     */
    @KafkaListener(topics = "external.audit.events", groupId = "external-audit-processor")  
    public void processExternalAuditEvent(String eventJson) {
        logger.debug("Processing external audit event");
        
        try {
            // Support complex nested objects from external systems
            Object event = objectMapper.readValue(eventJson, Object.class);
            
            // Store external events for compliance reporting
            auditService.auditSystemAction("EXTERNAL_EVENT_RECEIVED", "ExternalAudit", eventJson);
            
        } catch (Exception e) {
            logger.error("Error processing external audit event", e);
        }
    }

    private void handlePaymentSubmitted(String actorId, Object details) {
        logger.info("Payment submitted by actor: {}", actorId);
        // Additional processing logic here
    }

    private void handlePaymentApproved(String actorId, Object details) {
        logger.info("Payment approved by actor: {}", actorId);
        // Additional processing logic here
    }

    private void handleComplianceAlert(String actorId, Object details) {
        logger.warn("Compliance alert from actor: {}", actorId);
        // Additional alerting logic here
    }
}

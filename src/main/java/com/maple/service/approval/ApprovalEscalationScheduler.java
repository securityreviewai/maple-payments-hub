package com.maple.service.approval;

import com.maple.event.PaymentEvents;
import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import com.maple.repository.PaymentRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Raises stuck-approval escalations when pending approvals exceed configured business-time thresholds.
 */
@Service
public class ApprovalEscalationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalEscalationScheduler.class);

    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${maple.approval.repeat-escalation-hours:24}")
    private int repeatEscalationHours;

    @Autowired
    public ApprovalEscalationScheduler(PaymentRepository paymentRepository,
                                       AuditService auditService,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${maple.approval.escalation-scan-ms:300000}")
    @Transactional
    public void processEscalations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Payment> due = paymentRepository.findPendingApprovalEscalationsDue(PaymentStatus.PENDING_APPROVAL, now);
        if (due.isEmpty()) {
            return;
        }
        logger.info("Processing {} stuck-approval escalation(s)", due.size());
        for (Payment p : due) {
            int nextLevel = (p.getEscalationLevel() == null ? 0 : p.getEscalationLevel()) + 1;
            p.setEscalationLevel(nextLevel);
            p.setLastEscalationAt(now);
            p.setEscalationDueAt(now.plusHours(repeatEscalationHours));
            paymentRepository.save(p);

            auditService.auditPaymentEvent("SYSTEM", "PAYMENT_APPROVAL_ESCALATED", p.getId());

            publishEscalationEvent(p, nextLevel, now);
        }
    }

    private void publishEscalationEvent(Payment payment, int escalationLevel, OffsetDateTime now) {
        try {
            PaymentEvents.PaymentApprovalEscalated event = new PaymentEvents.PaymentApprovalEscalated(
                    payment.getId(),
                    payment.getPaymentReference(),
                    escalationLevel,
                    payment.getCreatedAt(),
                    "Pending approval exceeded configured threshold"
            );
            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published approval escalation for payment: {}", payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish approval escalation event", e);
        }
    }
}

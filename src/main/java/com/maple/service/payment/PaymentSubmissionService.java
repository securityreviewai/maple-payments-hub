package com.maple.service.payment;

import com.maple.dto.PaymentRequestDto;
import com.maple.dto.PaymentResponseDto;
import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import com.maple.model.User;
import com.maple.repository.PaymentRepository;
import com.maple.repository.UserRepository;
import com.maple.service.approval.ApprovalRulesEngineService;
import com.maple.service.audit.AuditService;
import com.maple.event.PaymentEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for payment submission and validation.
 * 
 * Handles the creation of new payments with business rule validation,
 * idempotency checking, and approval workflow initiation.
 */
@Service
@Transactional
public class PaymentSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentSubmissionService.class);

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${maple.payment.approval-threshold-cents:10000000}")
    private long approvalThresholdCents;

    @Value("${maple.payment.max-daily-limit-cents:1000000000}")
    private long maxDailyLimitCents;

    @Value("${maple.approval.default-holiday-calendar-id}")
    private String defaultHolidayCalendarId;

    @Value("${maple.approval.stuck-approval-hours:24}")
    private int stuckApprovalHours;

    private final ApprovalRulesEngineService approvalRulesEngineService;

    @Autowired
    public PaymentSubmissionService(PaymentRepository paymentRepository,
                                  UserRepository userRepository,
                                  AuditService auditService,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ApprovalRulesEngineService approvalRulesEngineService) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.approvalRulesEngineService = approvalRulesEngineService;
    }

    /**
     * Submits a new payment for processing.
     */
    public PaymentResponseDto submitPayment(PaymentRequestDto request, UUID initiatorId, String operationId) {
        logger.info("Submitting payment: {} for user: {}", request.getPaymentReference(), initiatorId);

        // Check for idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                logger.info("Idempotent payment request detected: {}", request.getIdempotencyKey());
                return convertToResponseDto(existing.get(), operationId);
            }
        }

        // Validate payment reference uniqueness
        Optional<Payment> existingByRef = paymentRepository.findByPaymentReference(request.getPaymentReference());
        if (existingByRef.isPresent()) {
            throw new PaymentValidationException("Payment reference already exists: " + request.getPaymentReference());
        }

        // Get user and validate
        User initiator = userRepository.findById(initiatorId)
                .orElseThrow(() -> new PaymentValidationException("Invalid user ID: " + initiatorId));

        if (!initiator.getIsActive()) {
            throw new PaymentValidationException("User account is not active");
        }

        // Validate business rules
        validatePaymentRequest(request, initiator);

        // Create payment entity
        Payment payment = createPaymentFromRequest(request, initiatorId);

        // Determine if approval is required
        boolean requiresApproval = determineApprovalRequirement(request, initiator);
        payment.evaluateApprovalRequirement(requiresApproval, approvalThresholdCents);
        if (Boolean.TRUE.equals(payment.getApprovalRequired())) {
            int tierApprovers = approvalRulesEngineService.resolveRequiredApprovers(
                    payment.getAmountCents(), payment.getCurrency());
            payment.setRequiredApprovers(Math.max(1, Math.min(2, tierApprovers)));
            if (defaultHolidayCalendarId != null && !defaultHolidayCalendarId.isBlank()) {
                try {
                    payment.setHolidayCalendarId(UUID.fromString(defaultHolidayCalendarId.trim()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid maple.approval.default-holiday-calendar-id, skipping calendar binding");
                }
            }
            payment.setEscalationDueAt(OffsetDateTime.now().plusHours(stuckApprovalHours));
        }

        // Save payment
        Payment savedPayment = paymentRepository.save(payment);
        logger.info("Created payment: {} with status: {}", savedPayment.getId(), savedPayment.getStatus());

        // Audit the creation
        auditService.auditPaymentCreated(
                initiatorId.toString(),
                savedPayment.getId(),
                savedPayment.getPaymentReference(),
                savedPayment.getAmountCents(),
                savedPayment.getCurrency()
        );

        // Publish event
        publishPaymentEvent(savedPayment, "PAYMENT_SUBMITTED");

        return convertToResponseDto(savedPayment, operationId);
    }

    /**
     * Validates payment request against business rules.
     */
    private void validatePaymentRequest(PaymentRequestDto request, User initiator) {
        // Validate amount limits
        if (request.getAmountCents() > maxDailyLimitCents) {
            throw new PaymentValidationException("Payment amount exceeds maximum daily limit");
        }

        // Check daily limits for user
        OffsetDateTime startOfDay = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endOfDay = startOfDay.plusDays(1);
        
        Long dailyTotal = paymentRepository.calculateTotalAmountForUserInPeriod(
                initiator.getId(), startOfDay, endOfDay);
        
        if (dailyTotal + request.getAmountCents() > maxDailyLimitCents) {
            throw new PaymentValidationException("Payment would exceed daily limit for user");
        }

        // Validate account formats (basic validation)
        if (!isValidAccountFormat(request.getDebtorAccount())) {
            throw new PaymentValidationException("Invalid debtor account format");
        }

        if (!isValidAccountFormat(request.getCreditorAccount())) {
            throw new PaymentValidationException("Invalid creditor account format");
        }

        // Additional business validations
        if (request.getDebtorAccount().equals(request.getCreditorAccount())) {
            throw new PaymentValidationException("Debtor and creditor accounts cannot be the same");
        }
    }

    /**
     * Determines if payment requires manual approval.
     */
    private boolean determineApprovalRequirement(PaymentRequestDto request, User initiator) {
        // High-value payments always require approval
        if (request.getAmountCents() > approvalThresholdCents) {
            return true;
        }

        // User-specific approval requirements
        if (initiator.requiresApprovalForAmount(request.getAmountCents())) {
            return true;
        }

        // Cross-border payments might require approval (simplified check)
        if (!request.getCurrency().equals("USD")) {
            return true;
        }

        return false;
    }

    /**
     * Creates Payment entity from request DTO.
     */
    private Payment createPaymentFromRequest(PaymentRequestDto request, UUID initiatorId) {
        Payment payment = new Payment();
        payment.setPaymentReference(request.getPaymentReference());
        payment.setAmountCents(request.getAmountCents());
        payment.setCurrency(request.getCurrency());
        payment.setDebtorAccount(request.getDebtorAccount());
        payment.setCreditorAccount(request.getCreditorAccount());
        payment.setCreditorName(request.getCreditorName());
        payment.setPaymentPurpose(request.getPaymentPurpose());
        payment.setInitiatedBy(initiatorId);
        payment.setIdempotencyKey(request.getIdempotencyKey());

        // Store metadata as JSON if provided
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                payment.setRawPayload(mapper.writeValueAsString(request.getMetadata()));
            } catch (Exception e) {
                logger.warn("Failed to serialize payment metadata", e);
            }
        }

        return payment;
    }

    /**
     * Converts Payment entity to response DTO.
     */
    private PaymentResponseDto convertToResponseDto(Payment payment, String operationId) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .amountCents(payment.getAmountCents())
                .formattedAmount(formatAmount(payment.getAmountCents(), payment.getCurrency()))
                .currency(payment.getCurrency())
                .debtorAccount(payment.getDebtorAccount())
                .creditorAccount(payment.getCreditorAccount())
                .creditorName(payment.getCreditorName())
                .paymentPurpose(payment.getPaymentPurpose())
                .status(payment.getStatus())
                .initiatedBy(payment.getInitiatedBy())
                .approvedBy(payment.getApprovedBy())
                .approvalRequired(payment.getApprovalRequired())
                .requiredApprovers(payment.getRequiredApprovers())
                .firstApprovalBy(payment.getFirstApprovalBy())
                .firstApprovalAt(payment.getFirstApprovalAt())
                .escalationDueAt(payment.getEscalationDueAt())
                .escalationLevel(payment.getEscalationLevel())
                .submittedAt(payment.getSubmittedAt())
                .settledAt(payment.getSettledAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .operationId(operationId)
                .build();
    }

    /**
     * Publishes payment event to Kafka.
     */
    private void publishPaymentEvent(Payment payment, String eventType) {
        try {
            PaymentEvents.PaymentSubmitted event = new PaymentEvents.PaymentSubmitted(
                    payment.getId(),
                    payment.getPaymentReference(),
                    payment.getAmountCents(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getApprovalRequired(),
                    payment.getInitiatedBy()
            );

            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
            logger.debug("Published payment event: {} for payment: {}", eventType, payment.getId());
        } catch (Exception e) {
            logger.error("Failed to publish payment event", e);
        }
    }

    /**
     * Basic account format validation.
     */
    private boolean isValidAccountFormat(String account) {
        if (account == null || account.trim().isEmpty()) {
            return false;
        }
        // Basic validation - can be enhanced for specific formats
        return account.matches("^[A-Z0-9-]+$") && account.length() >= 4 && account.length() <= 64;
    }

    /**
     * Formats amount for display.
     */
    private String formatAmount(Long amountCents, String currency) {
        if (amountCents == null) return "0.00";
        return String.format("%.2f %s", amountCents / 100.0, currency);
    }

    /**
     * Exception for payment validation errors.
     */
    public static class PaymentValidationException extends RuntimeException {
        public PaymentValidationException(String message) {
            super(message);
        }

        public PaymentValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

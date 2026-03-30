package com.maple.service.sftp;

import com.maple.config.MapleSftpProperties;
import com.maple.dto.PaymentBatchDto;
import com.maple.event.PaymentEvents;
import com.maple.model.Payment;
import com.maple.model.PaymentBatch;
import com.maple.model.SftpBatchDeliveryStatus;
import com.maple.repository.BatchDeliveryAttemptRepository;
import com.maple.repository.PaymentBatchRepository;
import com.maple.repository.PaymentRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates ISO20022 batch file generation, SFTP upload with retries, receipt polling, and API surfaces.
 */
@Service
public class PaymentBatchDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentBatchDeliveryService.class);

    private final MapleSftpProperties sftpProperties;
    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentRepository paymentRepository;
    private final BatchDeliveryAttemptRepository attemptRepository;
    private final PaymentBatchStateService batchStateService;
    private final SftpFileTransferClient sftpClient;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${maple.payment.batch-size:100}")
    private int batchSize;

    @Value("${maple.payment.batch-creator-user-id:00000000-0000-0000-0000-000000000001}")
    private UUID batchCreatorUserId;

    public PaymentBatchDeliveryService(
            MapleSftpProperties sftpProperties,
            PaymentBatchRepository paymentBatchRepository,
            PaymentRepository paymentRepository,
            BatchDeliveryAttemptRepository attemptRepository,
            PaymentBatchStateService batchStateService,
            SftpFileTransferClient sftpClient,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.sftpProperties = sftpProperties;
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.batchStateService = batchStateService;
        this.sftpClient = sftpClient;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Scheduled job: assemble a batch from approved payments and attempt SFTP delivery.
     */
    public void runScheduledBatchCreation() {
        if (!sftpProperties.getBatch().isEnabled()) {
            return;
        }
        Optional<PaymentBatch> created = batchStateService.createBatchFromApprovedQueue(batchSize, batchCreatorUserId);
        if (created.isEmpty()) {
            return;
        }
        PaymentBatch batch = created.get();
        logger.info(
                "Created payment batch {} with {} payment(s)",
                batch.getBatchReference(),
                batch.getPaymentCount());
        deliverBatch(batch.getId(), "SYSTEM");
    }

    /**
     * Retry scheduler: re-attempt SFTP for batches in {@link SftpBatchDeliveryStatus#UPLOAD_FAILED}.
     */
    public void processScheduledRetries() {
        if (!sftpProperties.getBatch().isEnabled()) {
            return;
        }
        List<PaymentBatch> due =
                paymentBatchRepository.findBySftpDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        SftpBatchDeliveryStatus.UPLOAD_FAILED, OffsetDateTime.now(), PageRequest.of(0, 10));
        for (PaymentBatch b : due) {
            logger.info("Retrying SFTP delivery for batch {}", b.getBatchReference());
            deliverBatch(b.getId(), "SYSTEM");
        }
    }

    /**
     * Poll SFTP receipts subdirectory for partner acknowledgment files.
     */
    public void pollReceipts() {
        if (!sftpProperties.getBatch().isEnabled()) {
            return;
        }
        String suffix = sftpProperties.getBatch().getReceiptSuffix();
        if (suffix == null || suffix.isBlank()) {
            return;
        }
        String subdir = sftpProperties.getBatch().getReceiptsSubdir().replaceFirst("^/+", "").replaceAll("/+$", "");
        List<PaymentBatch> waiting =
                paymentBatchRepository.findAwaitingReceipt(
                        SftpBatchDeliveryStatus.RECEIPT_PENDING, PageRequest.of(0, 25));
        for (PaymentBatch b : waiting) {
            String rel = subdir + "/" + b.getBatchReference() + suffix;
            try {
                if (sftpClient.fileExists(rel)) {
                    byte[] data = sftpClient.download(rel);
                    batchStateService.recordReceipt(b.getId(), rel, data);
                    auditService.auditSftpOperation("RECEIPT_DOWNLOAD", rel, true);
                    logger.info("Recorded delivery receipt for batch {}", b.getBatchReference());
                }
            } catch (Exception e) {
                logger.debug("Receipt not available yet for batch {}: {}", b.getBatchReference(), e.getMessage());
            }
        }
    }

    /**
     * Manual or operator-triggered retry (actor is JWT principal id or system).
     */
    public PaymentBatchDto retryDelivery(String batchReference, UUID actorId) {
        PaymentBatch b =
                paymentBatchRepository
                        .findByBatchReference(batchReference)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown batch: " + batchReference));
        if (b.getSftpDeliveryStatus() != SftpBatchDeliveryStatus.UPLOAD_FAILED
                && b.getSftpDeliveryStatus() != SftpBatchDeliveryStatus.PENDING_BUILD) {
            throw new IllegalStateException(
                    "Batch cannot be retried in state: " + b.getSftpDeliveryStatus());
        }
        deliverBatch(b.getId(), actorId != null ? actorId.toString() : "SYSTEM");
        return toDto(paymentBatchRepository.findById(b.getId()).orElseThrow());
    }

    /**
     * Submit a single approved payment as its own batch (clearing operator action).
     */
    public Payment submitSinglePaymentToClearing(UUID paymentId, UUID actorId) {
        if (!sftpProperties.getBatch().isEnabled()) {
            throw new IllegalStateException("SFTP batch delivery is disabled (maple.sftp.batch.enabled=false)");
        }
        PaymentBatch batch = batchStateService.createBatchForSinglePayment(paymentId, actorId);
        deliverBatch(batch.getId(), actorId.toString());
        return paymentRepository.findById(paymentId).orElseThrow();
    }

    public Page<PaymentBatchDto> listBatches(Pageable pageable) {
        return paymentBatchRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    public Optional<PaymentBatchDto> getBatch(String batchReference) {
        return paymentBatchRepository.findByBatchReference(batchReference).map(this::toDto);
    }

    public Optional<SftpBatchDeliveryStatus> getDeliveryStatusForBatchRef(String batchReference) {
        return paymentBatchRepository
                .findByBatchReference(batchReference)
                .map(PaymentBatch::getSftpDeliveryStatus);
    }

    void deliverBatch(UUID batchPk, String actorLabel) {
        PaymentBatch batch = paymentBatchRepository.findById(batchPk).orElseThrow();
        if (batch.getSftpDeliveryStatus() == SftpBatchDeliveryStatus.RECEIPT_PENDING
                || batch.getSftpDeliveryStatus() == SftpBatchDeliveryStatus.RECEIPT_CONFIRMED) {
            logger.debug("Batch {} file already on SFTP, skipping re-upload", batch.getBatchReference());
            return;
        }
        List<Payment> payments = paymentRepository.findByBatchId(batch.getBatchReference());
        if (payments.isEmpty()) {
            logger.warn("No payments for batch {}, skipping delivery", batch.getBatchReference());
            return;
        }
        byte[] payload =
                Iso20022PainFileGenerator.buildDocument(batch.getBatchReference(), payments);
        String relativeFile = batch.getIso20022Filename();
        batchStateService.markBatchUploading(batchPk);

        MapleSftpProperties.BatchDelivery bd = sftpProperties.getBatch();
        int maxImmediate = Math.max(1, bd.getUploadImmediateAttempts());
        long backoff = Math.max(100L, bd.getUploadImmediateBackoffMs());
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxImmediate; attempt++) {
            try {
                sftpClient.uploadUnderRemoteDir(relativeFile, payload);
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                logger.warn(
                        "SFTP upload attempt {}/{} failed for {}: {}",
                        attempt,
                        maxImmediate,
                        batch.getBatchReference(),
                        e.getMessage());
                if (attempt < maxImmediate) {
                    try {
                        Thread.sleep(backoff * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        lastError = ie;
                        break;
                    }
                }
            }
        }

        if (lastError != null) {
            String msg = lastError.getMessage() != null ? lastError.getMessage() : lastError.toString();
            PaymentBatch fresh = paymentBatchRepository.findById(batchPk).orElseThrow();
            batchStateService.recordUploadFailure(
                    batchPk, msg, fresh.getDeliveryRetryCount() + 1, relativeFile);
            auditService.auditSftpOperation("UPLOAD", relativeFile, false);
            return;
        }

        batchStateService.recordUploadSuccess(
                batchPk, relativeFile, payments, batch.getIso20022Filename(), batch.getBatchReference());
        auditService.auditSftpOperation("UPLOAD", relativeFile, true);

        for (Payment p : payments) {
            publishClearingEvent(p, batch.getBatchReference());
        }
    }

    private void publishClearingEvent(Payment payment, String batchRef) {
        try {
            PaymentEvents.PaymentSubmittedToClearing event =
                    new PaymentEvents.PaymentSubmittedToClearing(
                            payment.getId(),
                            payment.getPaymentReference(),
                            batchRef,
                            batchRef,
                            "SFTP");
            kafkaTemplate.send("payments.events", payment.getId().toString(), event);
        } catch (Exception e) {
            logger.error("Failed to publish clearing event for {}", payment.getId(), e);
        }
    }

    private PaymentBatchDto toDto(PaymentBatch b) {
        PaymentBatchDto dto = new PaymentBatchDto();
        dto.setId(b.getId());
        dto.setBatchReference(b.getBatchReference());
        dto.setBatchType(b.getBatchType());
        dto.setStatus(b.getStatus());
        dto.setPaymentCount(b.getPaymentCount());
        dto.setTotalAmountCents(b.getTotalAmountCents());
        dto.setIso20022Filename(b.getIso20022Filename());
        dto.setSftpUploadedAt(b.getSftpUploadedAt());
        dto.setSftpDeliveryStatus(b.getSftpDeliveryStatus());
        dto.setSftpRemotePath(b.getSftpRemotePath());
        dto.setSftpLastError(b.getSftpLastError());
        dto.setDeliveryRetryCount(b.getDeliveryRetryCount());
        dto.setMaxDeliveryRetries(b.getMaxDeliveryRetries());
        dto.setNextRetryAt(b.getNextRetryAt());
        dto.setReceiptRemotePath(b.getReceiptRemotePath());
        dto.setReceiptReceivedAt(b.getReceiptReceivedAt());
        dto.setReceiptPreview(b.getReceiptPreview());
        dto.setCreatedAt(b.getCreatedAt());
        dto.setSubmittedAt(b.getSubmittedAt());
        dto.setProcessedAt(b.getProcessedAt());
        dto.setRecentAttempts(
                attemptRepository.findByBatchReferenceOrderByAttemptNumberDesc(b.getBatchReference()).stream()
                        .limit(20)
                        .map(
                                a -> {
                                    PaymentBatchDto.DeliveryAttemptDto d = new PaymentBatchDto.DeliveryAttemptDto();
                                    d.setAttemptNumber(a.getAttemptNumber());
                                    d.setPhase(a.getPhase());
                                    d.setOutcome(a.getOutcome());
                                    d.setDetailMessage(a.getDetailMessage());
                                    d.setRemotePath(a.getRemotePath());
                                    d.setFinishedAt(a.getFinishedAt());
                                    return d;
                                })
                        .collect(Collectors.toList()));
        return dto;
    }
}

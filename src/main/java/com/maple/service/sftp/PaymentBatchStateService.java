package com.maple.service.sftp;

import com.maple.config.MapleSftpProperties;
import com.maple.model.BatchDeliveryAttempt;
import com.maple.model.Payment;
import com.maple.model.PaymentBatch;
import com.maple.model.SftpBatchDeliveryStatus;
import com.maple.model.PaymentStatus;
import com.maple.repository.BatchDeliveryAttemptRepository;
import com.maple.repository.PaymentBatchRepository;
import com.maple.repository.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional state transitions for {@link PaymentBatch} and related {@link Payment} rows.
 */
@Service
public class PaymentBatchStateService {

    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentRepository paymentRepository;
    private final BatchDeliveryAttemptRepository attemptRepository;
    private final MapleSftpProperties sftpProperties;

    public PaymentBatchStateService(
            PaymentBatchRepository paymentBatchRepository,
            PaymentRepository paymentRepository,
            BatchDeliveryAttemptRepository attemptRepository,
            MapleSftpProperties sftpProperties) {
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.sftpProperties = sftpProperties;
    }

    /**
     * Locks approved payments waiting for batching and binds them to a new batch row (scheduled job).
     */
    @Transactional
    public Optional<PaymentBatch> createBatchFromApprovedQueue(int batchSize, UUID createdBy) {
        List<Payment> slice =
                paymentRepository.findReadyForBatchingForUpdate(PageRequest.of(0, Math.max(1, batchSize)));
        if (slice.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(insertBatchAndAssignPayments(slice, createdBy));
    }

    /**
     * Single-payment clearing submission: locks the payment and creates a one-item batch.
     */
    @Transactional
    public PaymentBatch createBatchForSinglePayment(UUID paymentId, UUID actorId) {
        Payment payment =
                paymentRepository
                        .findByIdForUpdate(paymentId)
                        .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment must be APPROVED");
        }
        if (payment.getBatchId() != null) {
            throw new IllegalStateException("Payment is already assigned to batch " + payment.getBatchId());
        }
        return insertBatchAndAssignPayments(List.of(payment), actorId);
    }

    private PaymentBatch insertBatchAndAssignPayments(List<Payment> payments, UUID createdBy) {
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("payments");
        }
        String batchRef =
                "BATCH-"
                        + DateTimeFormatter.BASIC_ISO_DATE.format(OffsetDateTime.now().toLocalDate())
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String isoName = Iso20022PainFileGenerator.buildFilename(batchRef);
        long total = payments.stream().mapToLong(Payment::getAmountCents).sum();

        PaymentBatch batch = new PaymentBatch();
        batch.setId(UUID.randomUUID());
        batch.setBatchReference(batchRef);
        batch.setBatchType("OUTBOUND");
        batch.setStatus("BUILDING");
        batch.setPaymentCount(payments.size());
        batch.setTotalAmountCents(total);
        batch.setIso20022Filename(isoName);
        batch.setSftpDeliveryStatus(SftpBatchDeliveryStatus.PENDING_BUILD);
        batch.setCreatedBy(createdBy);
        batch.setCreatedAt(OffsetDateTime.now());
        batch.setMaxDeliveryRetries(sftpProperties.getBatch().getMaxRetries());
        batch.setDeliveryRetryCount(0);
        paymentBatchRepository.save(batch);

        for (Payment p : payments) {
            p.setBatchId(batchRef);
            paymentRepository.save(p);
        }
        return batch;
    }

    @Transactional
    public void markBatchUploading(UUID batchPk) {
        PaymentBatch b = paymentBatchRepository.findById(batchPk).orElseThrow();
        b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.UPLOADING);
        b.setStatus("UPLOADING");
        paymentBatchRepository.save(b);
    }

    @Transactional
    public void recordUploadSuccess(
            UUID batchPk,
            String remoteRelativePath,
            List<Payment> payments,
            String isoFilename,
            String batchReference) {
        PaymentBatch b = paymentBatchRepository.findById(batchPk).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        b.setSftpUploadedAt(now);
        b.setSftpRemotePath(remoteRelativePath);
        b.setSftpLastError(null);
        b.setStatus("SUBMITTED");
        b.setSubmittedAt(now);

        MapleSftpProperties.BatchDelivery bd = sftpProperties.getBatch();
        if (bd.getReceiptSuffix() == null || bd.getReceiptSuffix().isBlank()) {
            b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.RECEIPT_CONFIRMED);
            b.setReceiptReceivedAt(now);
            b.setReceiptPreview("Receipt polling disabled (maple.sftp.batch.receipt-suffix empty)");
        } else {
            b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.RECEIPT_PENDING);
        }

        paymentBatchRepository.save(b);

        for (Payment p : payments) {
            p.markSubmitted(batchReference, isoFilename);
            paymentRepository.save(p);
        }
    }

    @Transactional
    public void recordUploadFailure(
            UUID batchPk,
            String errorDetail,
            int deliveryAttemptNumber,
            String remotePathAttempt) {
        PaymentBatch b = paymentBatchRepository.findById(batchPk).orElseThrow();
        b.setSftpLastError(truncate(errorDetail, 8000));
        int retries = b.getDeliveryRetryCount() + 1;
        b.setDeliveryRetryCount(retries);
        long base = sftpProperties.getBatch().getRetryBaseDelayMs();
        long delayMs = base * Math.max(1, retries);

        boolean dead = retries >= b.getMaxDeliveryRetries();
        if (dead) {
            b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.DEAD_LETTER);
            b.setNextRetryAt(null);
            b.setStatus("DEAD_LETTER");
            paymentRepository.clearBatchAssignment(b.getBatchReference());
        } else {
            b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.UPLOAD_FAILED);
            b.setStatus("RETRY_WAIT");
            b.setNextRetryAt(OffsetDateTime.now().plusSeconds(Math.max(1, delayMs / 1000)));
        }
        paymentBatchRepository.save(b);

        BatchDeliveryAttempt a = new BatchDeliveryAttempt();
        a.setId(UUID.randomUUID());
        a.setBatchReference(b.getBatchReference());
        a.setAttemptNumber(deliveryAttemptNumber);
        a.setPhase("SFTP_UPLOAD");
        OffsetDateTime finished = OffsetDateTime.now();
        a.setStartedAt(finished.minusSeconds(1));
        a.setFinishedAt(finished);
        a.setOutcome(dead ? "DEAD_LETTER" : "FAILED");
        a.setDetailMessage(truncate(errorDetail, 4000));
        a.setRemotePath(remotePathAttempt);
        attemptRepository.save(a);
    }

    @Transactional
    public void recordReceipt(UUID batchPk, String receiptRelativePath, byte[] receiptBytes) {
        PaymentBatch b = paymentBatchRepository.findById(batchPk).orElseThrow();
        int max = sftpProperties.getBatch().getReceiptPreviewMaxChars();
        String full = new String(receiptBytes, java.nio.charset.StandardCharsets.UTF_8);
        String preview = full.length() <= max ? full : full.substring(0, max);
        b.setReceiptRemotePath(receiptRelativePath);
        b.setReceiptPreview(preview);
        b.setReceiptReceivedAt(OffsetDateTime.now());
        b.setSftpDeliveryStatus(SftpBatchDeliveryStatus.RECEIPT_CONFIRMED);
        b.setProcessedAt(OffsetDateTime.now());
        b.setStatus("PROCESSED");
        paymentBatchRepository.save(b);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}

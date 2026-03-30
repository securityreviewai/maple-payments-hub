package com.maple.repository;

import com.maple.model.PaymentBatch;
import com.maple.model.SftpBatchDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, UUID> {

    Optional<PaymentBatch> findByBatchReference(String batchReference);

    Page<PaymentBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PaymentBatch> findBySftpDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            SftpBatchDeliveryStatus status, OffsetDateTime now, Pageable pageable);

    @Query(
            "SELECT b FROM PaymentBatch b WHERE b.sftpDeliveryStatus = :status "
                    + "AND b.receiptReceivedAt IS NULL ORDER BY b.sftpUploadedAt ASC")
    List<PaymentBatch> findAwaitingReceipt(
            @Param("status") SftpBatchDeliveryStatus status, Pageable pageable);
}

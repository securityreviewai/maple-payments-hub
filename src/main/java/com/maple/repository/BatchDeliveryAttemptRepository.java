package com.maple.repository;

import com.maple.model.BatchDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BatchDeliveryAttemptRepository extends JpaRepository<BatchDeliveryAttempt, UUID> {

    List<BatchDeliveryAttempt> findByBatchReferenceOrderByAttemptNumberDesc(String batchReference);
}

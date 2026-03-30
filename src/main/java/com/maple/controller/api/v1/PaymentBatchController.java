package com.maple.controller.api.v1;

import com.maple.dto.PaymentBatchDto;
import com.maple.service.sftp.PaymentBatchDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API for outbound payment batch assembly, SFTP delivery status, retries, and receipts.
 */
@RestController
@RequestMapping("/api/v1/payment-batches")
@Tag(name = "Payment Batches", description = "SFTP batch delivery and receipts")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class PaymentBatchController {

    private final PaymentBatchDeliveryService deliveryService;

    public PaymentBatchController(PaymentBatchDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @Operation(summary = "List payment batches (newest first)")
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<Page<PaymentBatchDto>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(deliveryService.listBatches(pageable));
    }

    @GetMapping("/{batchReference}")
    @Operation(summary = "Get batch by reference including SFTP and receipt status")
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<PaymentBatchDto> get(@PathVariable String batchReference) {
        return deliveryService
                .getBatch(batchReference)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{batchReference}/retry-delivery")
    @Operation(summary = "Retry SFTP upload for a failed batch")
    @PreAuthorize("hasAuthority('ROLE_CLEARING')")
    public ResponseEntity<PaymentBatchDto> retry(
            @PathVariable String batchReference, Authentication authentication) {
        UUID actor = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(deliveryService.retryDelivery(batchReference, actor));
    }
}

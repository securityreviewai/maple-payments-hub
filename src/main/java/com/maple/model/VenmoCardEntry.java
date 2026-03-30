package com.maple.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Venmo-sourced transaction entry for user card/spending visualization.
 * No raw card numbers or PII—only amounts, dates, and descriptions for charts.
 */
@Entity
@Table(name = "venmo_card_entries", indexes = {
    @Index(name = "idx_venmo_card_entries_user_date", columnList = "userId,transactionDate"),
    @Index(name = "idx_venmo_card_entries_user_id", columnList = "userId")
})
public class VenmoCardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;

    @Column(name = "transaction_date", nullable = false)
    @NotNull
    private LocalDate transactionDate;

    @Column(name = "amount_cents", nullable = false)
    @NotNull
    private Long amountCents;

    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;

    @Column(name = "entry_type", length = 64)
    @Size(max = 64)
    private String entryType = "payment";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public VenmoCardEntry() {}

    public VenmoCardEntry(UUID userId, LocalDate transactionDate, Long amountCents, String description, String entryType) {
        this.userId = userId;
        this.transactionDate = transactionDate;
        this.amountCents = amountCents;
        this.description = description != null && description.length() > 500 ? description.substring(0, 500) : description;
        this.entryType = entryType != null && entryType.length() > 64 ? entryType.substring(0, 64) : (entryType != null ? entryType : "payment");
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description != null && description.length() > 500 ? description.substring(0, 500) : description;
    }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) {
        this.entryType = entryType != null && entryType.length() > 64 ? entryType.substring(0, 64) : (entryType != null ? entryType : "payment");
    }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

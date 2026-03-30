package com.maple.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a single line entry from an uploaded bank statement.
 *
 * Each entry undergoes a reconciliation matching process to find the corresponding
 * internal {@link Payment}. The outcome is recorded in {@code reconciliationStatus}
 * along with the confidence level of any automatic match.
 */
@Entity
@Table(name = "bank_statement_entries", indexes = {
    @Index(name = "idx_bse_statement_id", columnList = "statementId"),
    @Index(name = "idx_bse_payment_reference", columnList = "paymentReference"),
    @Index(name = "idx_bse_reconciliation_status", columnList = "reconciliationStatus"),
    @Index(name = "idx_bse_transaction_date", columnList = "transactionDate"),
    @Index(name = "idx_bse_currency_amount", columnList = "currency, amountCents")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BankStatementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "statement_id", nullable = false, length = 64)
    @NotBlank
    @Size(max = 64)
    private String statementId;

    @Column(name = "transaction_date", nullable = false)
    @NotNull
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "transaction_reference", length = 128)
    @Size(max = 128)
    private String transactionReference;

    /** Our internal payment reference, when present in the bank narrative. */
    @Column(name = "payment_reference", length = 64)
    @Size(max = 64)
    private String paymentReference;

    @Column(name = "amount_cents", nullable = false)
    @NotNull
    @Positive
    private Long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    /** DEBIT or CREDIT from the account-holder's perspective. */
    @Column(name = "debit_credit", nullable = false, length = 6)
    @NotBlank
    @Pattern(regexp = "DEBIT|CREDIT", message = "Must be DEBIT or CREDIT")
    private String debitCredit;

    @Column(name = "counterparty_account", length = 64)
    @Size(max = 64)
    private String counterpartyAccount;

    @Column(name = "counterparty_name", length = 140)
    @Size(max = 140)
    private String counterpartyName;

    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false)
    @NotNull
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_payment_id")
    private Payment matchedPayment;

    /** HIGH (exact reference + amount match), MEDIUM (fuzzy amount + date match), LOW. */
    @Column(name = "match_confidence", length = 20)
    private String matchConfidence;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    @Column(name = "matched_by")
    private UUID matchedBy;

    @Column(name = "uploaded_by", nullable = false)
    @NotNull
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public BankStatementEntry() {}

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getStatementId() { return statementId; }
    public void setStatementId(String statementId) { this.statementId = statementId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDebitCredit() { return debitCredit; }
    public void setDebitCredit(String debitCredit) { this.debitCredit = debitCredit; }

    public String getCounterpartyAccount() { return counterpartyAccount; }
    public void setCounterpartyAccount(String counterpartyAccount) {
        this.counterpartyAccount = counterpartyAccount;
    }

    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public Payment getMatchedPayment() { return matchedPayment; }
    public void setMatchedPayment(Payment matchedPayment) { this.matchedPayment = matchedPayment; }

    public String getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(String matchConfidence) { this.matchConfidence = matchConfidence; }

    public OffsetDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(OffsetDateTime matchedAt) { this.matchedAt = matchedAt; }

    public UUID getMatchedBy() { return matchedBy; }
    public void setMatchedBy(UUID matchedBy) { this.matchedBy = matchedBy; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankStatementEntry that = (BankStatementEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "BankStatementEntry{" +
               "id=" + id +
               ", statementId='" + statementId + '\'' +
               ", transactionDate=" + transactionDate +
               ", amountCents=" + amountCents +
               ", currency='" + currency + '\'' +
               ", reconciliationStatus=" + reconciliationStatus +
               '}';
    }
}

package com.maple.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "approval_amount_tiers")
public class ApprovalAmountTier {

    @Id
    private UUID id;

    @Column(name = "currency", nullable = false, length = 3)
    @NotNull
    private String currency;

    @Column(name = "min_amount_cents", nullable = false)
    private long minAmountCents;

    @Column(name = "max_amount_cents")
    private Long maxAmountCents;

    @Column(name = "required_approvers", nullable = false)
    private int requiredApprovers;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getMinAmountCents() {
        return minAmountCents;
    }

    public void setMinAmountCents(long minAmountCents) {
        this.minAmountCents = minAmountCents;
    }

    public Long getMaxAmountCents() {
        return maxAmountCents;
    }

    public void setMaxAmountCents(Long maxAmountCents) {
        this.maxAmountCents = maxAmountCents;
    }

    public int getRequiredApprovers() {
        return requiredApprovers;
    }

    public void setRequiredApprovers(int requiredApprovers) {
        this.requiredApprovers = requiredApprovers;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}

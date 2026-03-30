package com.maple.service.approval;

import com.maple.model.ApprovalAmountTier;
import com.maple.repository.ApprovalAmountTierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves amount-based approval rules (threshold tiers and dual control).
 */
@Service
public class ApprovalRulesEngineService {

    private final ApprovalAmountTierRepository approvalAmountTierRepository;

    @Autowired
    public ApprovalRulesEngineService(ApprovalAmountTierRepository approvalAmountTierRepository) {
        this.approvalAmountTierRepository = approvalAmountTierRepository;
    }

    /**
     * Returns required distinct approvers (1 or 2) for an amount and currency based on configured tiers.
     */
    public int resolveRequiredApprovers(long amountCents, String currency) {
        String ccy = currency != null ? currency.toUpperCase() : "USD";
        List<ApprovalAmountTier> tiers = approvalAmountTierRepository.findByCurrencyOrderBySortOrderAsc(ccy);
        if (tiers.isEmpty()) {
            return 1;
        }
        for (ApprovalAmountTier t : tiers) {
            if (amountCents >= t.getMinAmountCents()
                    && (t.getMaxAmountCents() == null || amountCents <= t.getMaxAmountCents())) {
                return t.getRequiredApprovers();
            }
        }
        return 1;
    }
}

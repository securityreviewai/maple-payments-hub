package com.maple.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing partner bank profiles and routing preferences.
 * 
 * Handles partner-specific requirements for:
 * - Message formats (Fedwire, CHIPS, SWIFT)
 * - Signing requirements and certificates
 * - Routing preferences and fallback options
 * - Settlement instructions and cut-off times
 */
@Service
public class PartnerProfileService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerProfileService.class);

    // Hardcoded partner profiles for demo - in production these would come from database
    private final Map<String, PartnerProfile> partnerProfiles;

    public PartnerProfileService() {
        this.partnerProfiles = initializePartnerProfiles();
    }

    public PartnerProfile getPartnerProfile(String bankCode) {
        PartnerProfile profile = partnerProfiles.get(bankCode);
        if (profile == null) {
            logger.warn("No profile found for bank code: {}, using default", bankCode);
            return getDefaultProfile();
        }
        return profile;
    }

    private Map<String, PartnerProfile> initializePartnerProfiles() {
        Map<String, PartnerProfile> profiles = new HashMap<>();

        // JPMorgan Chase
        profiles.put("021000021", PartnerProfile.builder()
            .partnerCode("JPMC")
            .bankName("JPMorgan Chase Bank")
            .fedwireRoutingNumber("021000021")
            .chipsRoutingCode("CH210002")
            .chipsSenderCode("CHASNYXX")
            .chipsReceiverCode("CHASNYXX")
            .requiresChipsFormat(true)
            .requiresMessageSigning(true)
            .maxTransactionAmount(1000000000L) // $10M
            .cutoffTimeLocal("15:30")
            .settlementAccount("USD-MAIN-001")
            .signingCertificate("JPMC-PROD-2024")
            .build());

        // Bank of America
        profiles.put("026009593", PartnerProfile.builder()
            .partnerCode("BOFA")
            .bankName("Bank of America")
            .fedwireRoutingNumber("026009593")
            .chipsRoutingCode("CH260095")
            .chipsSenderCode("BOFANYXX")
            .chipsReceiverCode("BOFANYXX")
            .requiresChipsFormat(false)
            .requiresMessageSigning(true)
            .maxTransactionAmount(500000000L) // $5M
            .cutoffTimeLocal("16:00")
            .settlementAccount("USD-MAIN-002")
            .signingCertificate("BOFA-PROD-2024")
            .build());

        // Wells Fargo
        profiles.put("121000248", PartnerProfile.builder()
            .partnerCode("WFARGO")
            .bankName("Wells Fargo Bank")
            .fedwireRoutingNumber("121000248")
            .chipsRoutingCode("CH121000")
            .chipsSenderCode("WFBINYXX")
            .chipsReceiverCode("WFBINYXX")
            .requiresChipsFormat(false)
            .requiresMessageSigning(false)
            .maxTransactionAmount(250000000L) // $2.5M
            .cutoffTimeLocal("15:45")
            .settlementAccount("USD-MAIN-003")
            .build());

        // Citibank
        profiles.put("021000089", PartnerProfile.builder()
            .partnerCode("CITI")
            .bankName("Citibank N.A.")
            .fedwireRoutingNumber("021000089")
            .chipsRoutingCode("CH210000")
            .chipsSenderCode("CITINYXX")
            .chipsReceiverCode("CITINYXX")
            .requiresChipsFormat(true)
            .requiresMessageSigning(true)
            .maxTransactionAmount(2000000000L) // $20M
            .cutoffTimeLocal("16:30")
            .settlementAccount("USD-MAIN-004")
            .signingCertificate("CITI-PROD-2024")
            .build());

        return profiles;
    }

    private PartnerProfile getDefaultProfile() {
        return PartnerProfile.builder()
            .partnerCode("DEFAULT")
            .bankName("Unknown Bank")
            .fedwireRoutingNumber("021000021")
            .requiresChipsFormat(false)
            .requiresMessageSigning(false)
            .maxTransactionAmount(100000000L) // $1M default limit
            .cutoffTimeLocal("15:00")
            .settlementAccount("USD-MAIN-DEFAULT")
            .build();
    }
}

/**
 * Partner bank profile configuration.
 */
class PartnerProfile {
    private String partnerCode;
    private String bankName;
    private String fedwireRoutingNumber;
    private String chipsRoutingCode;
    private String chipsSenderCode;
    private String chipsReceiverCode;
    private boolean requiresChipsFormat;
    private boolean requiresMessageSigning;
    private long maxTransactionAmount;
    private String cutoffTimeLocal;
    private String settlementAccount;
    private String signingCertificate;

    // Private constructor for builder pattern
    private PartnerProfile() {}

    // Static builder method
    public static PartnerProfileBuilder builder() {
        return new PartnerProfileBuilder();
    }

    // Getters
    public String getPartnerCode() { return partnerCode; }
    public String getBankName() { return bankName; }
    public String getFedwireRoutingNumber() { return fedwireRoutingNumber; }
    public String getChipsRoutingCode() { return chipsRoutingCode; }
    public String getChipsSenderCode() { return chipsSenderCode; }
    public String getChipsReceiverCode() { return chipsReceiverCode; }
    public boolean requiresChipsFormat() { return requiresChipsFormat; }
    public boolean requiresMessageSigning() { return requiresMessageSigning; }
    public long getMaxTransactionAmount() { return maxTransactionAmount; }
    public String getCutoffTimeLocal() { return cutoffTimeLocal; }
    public String getSettlementAccount() { return settlementAccount; }
    public String getSigningCertificate() { return signingCertificate; }

    // Builder class
    public static class PartnerProfileBuilder {
        private final PartnerProfile profile = new PartnerProfile();

        public PartnerProfileBuilder partnerCode(String partnerCode) {
            profile.partnerCode = partnerCode;
            return this;
        }

        public PartnerProfileBuilder bankName(String bankName) {
            profile.bankName = bankName;
            return this;
        }

        public PartnerProfileBuilder fedwireRoutingNumber(String fedwireRoutingNumber) {
            profile.fedwireRoutingNumber = fedwireRoutingNumber;
            return this;
        }

        public PartnerProfileBuilder chipsRoutingCode(String chipsRoutingCode) {
            profile.chipsRoutingCode = chipsRoutingCode;
            return this;
        }

        public PartnerProfileBuilder chipsSenderCode(String chipsSenderCode) {
            profile.chipsSenderCode = chipsSenderCode;
            return this;
        }

        public PartnerProfileBuilder chipsReceiverCode(String chipsReceiverCode) {
            profile.chipsReceiverCode = chipsReceiverCode;
            return this;
        }

        public PartnerProfileBuilder requiresChipsFormat(boolean requiresChipsFormat) {
            profile.requiresChipsFormat = requiresChipsFormat;
            return this;
        }

        public PartnerProfileBuilder requiresMessageSigning(boolean requiresMessageSigning) {
            profile.requiresMessageSigning = requiresMessageSigning;
            return this;
        }

        public PartnerProfileBuilder maxTransactionAmount(long maxTransactionAmount) {
            profile.maxTransactionAmount = maxTransactionAmount;
            return this;
        }

        public PartnerProfileBuilder cutoffTimeLocal(String cutoffTimeLocal) {
            profile.cutoffTimeLocal = cutoffTimeLocal;
            return this;
        }

        public PartnerProfileBuilder settlementAccount(String settlementAccount) {
            profile.settlementAccount = settlementAccount;
            return this;
        }

        public PartnerProfileBuilder signingCertificate(String signingCertificate) {
            profile.signingCertificate = signingCertificate;
            return this;
        }

        public PartnerProfile build() {
            return profile;
        }
    }
}

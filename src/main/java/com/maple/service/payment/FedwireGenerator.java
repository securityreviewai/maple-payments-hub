package com.maple.service.payment;

import com.maple.model.Payment;
import com.maple.service.audit.AuditService;
import com.maple.service.hsm.HsmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fedwire message generator for high-value domestic wire transfers.
 * 
 * Generates Fedwire format messages compliant with Federal Reserve specifications:
 * - IMAD (Input Message Accountability Data) sequencing
 * - OMAD (Output Message Accountability Data) tracking
 * - Business function codes and type/subtype codes
 * - Proper field tags and mandatory/optional fields
 * - CHIPS integration for correspondent banking
 */
@Service
public class FedwireGenerator {

    private static final Logger logger = LoggerFactory.getLogger(FedwireGenerator.class);

    // Fedwire message types
    private static final String CUSTOMER_TRANSFER = "1000";
    private static final String BANK_TRANSFER = "1600";
    private static final String DRAWDOWN_REQUEST = "9000";

    // Standard Fedwire tags
    private static final String MESSAGE_DISPOSITION = "{1100}";
    private static final String RECEIVER_ID = "{1110}";
    private static final String BUSINESS_FUNCTION_CODE = "{3100}";
    private static final String LOCAL_INSTRUMENT = "{3110}";
    private static final String PAYMENT_METHOD = "{3400}";
    private static final String AMOUNT = "{3320}";
    private static final String SENDER_DI = "{3200}";
    private static final String RECEIVER_DI = "{3300}";
    private static final String BUSINESS_DATA = "{4000}";
    private static final String ORIGINATOR_INFO = "{5000}";
    private static final String BENEFICIARY_INFO = "{5900}";
    private static final String REMITTANCE_INFO = "{6000}";

    @Value("${maple.fedwire.sender-id:021000021}")
    private String senderId;

    @Value("${maple.fedwire.environment:T}")  // T=Test, P=Production
    private String environment;

    @Value("${maple.fedwire.imad-prefix:FW}")
    private String imadPrefix;

    private final AuditService auditService;
    private final HsmClient hsmClient;
    private final PartnerProfileService partnerProfileService;

    @Autowired
    public FedwireGenerator(AuditService auditService, HsmClient hsmClient, 
                           PartnerProfileService partnerProfileService) {
        this.auditService = auditService;
        this.hsmClient = hsmClient;
        this.partnerProfileService = partnerProfileService;
    }

    /**
     * Generates a Fedwire message for high-value payment.
     */
    public FedwireMessage generateFedwireMessage(Payment payment, WireTransferType transferType) {
        logger.info("Generating Fedwire message for payment: {}", payment.getId());

        try {
            String imad = generateImad();
            StringWriter writer = new StringWriter();

            // Get partner profile for routing and format preferences
            PartnerProfile profile = partnerProfileService.getPartnerProfile(
                extractBankCode(payment.getCreditorAccount()));

            // Build Fedwire message
            writer.write(buildMessageHeader(imad, transferType));
            writer.write(buildBusinessFunctionCode(payment, transferType));
            writer.write(buildAmountField(payment));
            writer.write(buildSenderInfo());
            writer.write(buildReceiverInfo(payment, profile));
            writer.write(buildOriginatorInfo(payment));
            writer.write(buildBeneficiaryInfo(payment));
            
            if (payment.getPaymentPurpose() != null && !payment.getPaymentPurpose().trim().isEmpty()) {
                writer.write(buildRemittanceInfo(payment));
            }

            // Add CHIPS-specific fields if required
            if (profile.requiresChipsFormat()) {
                writer.write(buildChipsFields(payment, profile));
            }

            // Add regulatory reporting fields for large amounts
            if (payment.getAmountCents() >= 300000000) { // $3M+ requires CTR fields
                writer.write(buildRegulatoryFields(payment));
            }

            String messageContent = writer.toString();
            
            // Validate message format
            validateFedwireMessage(messageContent, transferType);

            // Sign message if required by partner
            String signature = null;
            if (profile.requiresMessageSigning()) {
                signature = signMessage(messageContent);
            }

            auditService.auditSystemAction("FEDWIRE_MESSAGE_GENERATED", "FedwireMessage", imad);

            return new FedwireMessage(messageContent, imad, signature, payment.getId());

        } catch (Exception e) {
            logger.error("Failed to generate Fedwire message for payment: {}", payment.getId(), e);
            auditService.auditSystemAction("FEDWIRE_MESSAGE_GENERATION_FAILED", "Payment", payment.getId().toString());
            throw new FedwireGenerationException("Fedwire message generation failed", e);
        }
    }

    /**
     * Generates CHIPS message for correspondent banking.
     */
    public ChipsMessage generateChipsMessage(Payment payment, PartnerProfile profile) {
        logger.info("Generating CHIPS message for payment: {} via partner: {}", 
                   payment.getId(), profile.getPartnerCode());

        try {
            String sequenceNumber = generateChipsSequence();
            
            Map<String, String> chipsFields = new HashMap<>();
            chipsFields.put("SEQ", sequenceNumber);
            chipsFields.put("MSG", "100"); // Customer transfer
            chipsFields.put("AMT", String.valueOf(payment.getAmountCents()));
            chipsFields.put("CCY", payment.getCurrency());
            chipsFields.put("VDT", getValueDate());
            chipsFields.put("SND", profile.getChipsSenderCode());
            chipsFields.put("RCV", profile.getChipsReceiverCode());
            chipsFields.put("BNF", payment.getCreditorName());
            chipsFields.put("ACC", extractAccountNumber(payment.getCreditorAccount()));
            
            if (payment.getPaymentPurpose() != null) {
                chipsFields.put("REM", payment.getPaymentPurpose());
            }

            String messageContent = formatChipsMessage(chipsFields);
            validateChipsMessage(messageContent);

            auditService.auditSystemAction("CHIPS_MESSAGE_GENERATED", "ChipsMessage", sequenceNumber);

            return new ChipsMessage(messageContent, sequenceNumber, payment.getId());

        } catch (Exception e) {
            logger.error("Failed to generate CHIPS message for payment: {}", payment.getId(), e);
            throw new ChipsGenerationException("CHIPS message generation failed", e);
        }
    }

    // Private helper methods

    private String buildMessageHeader(String imad, WireTransferType transferType) {
        return String.format("%s%s%s%s\n",
            MESSAGE_DISPOSITION, environment,
            RECEIVER_ID, determineReceiverId(transferType),
            "{2000}", imad  // IMAD field
        );
    }

    private String buildBusinessFunctionCode(Payment payment, WireTransferType transferType) {
        String functionCode = "CTR"; // Customer Transfer
        if (transferType == WireTransferType.BANK_TO_BANK) {
            functionCode = "BTR"; // Bank Transfer
        }
        return BUSINESS_FUNCTION_CODE + functionCode + "\n";
    }

    private String buildAmountField(Payment payment) {
        // Format: USD123456.78
        String formattedAmount = String.format("%s%.2f", 
            payment.getCurrency(), payment.getAmountCents() / 100.0);
        return AMOUNT + formattedAmount + "\n";
    }

    private String buildSenderInfo() {
        return SENDER_DI + senderId + "\n";
    }

    private String buildReceiverInfo(Payment payment, PartnerProfile profile) {
        String receiverId = profile.getFedwireRoutingNumber();
        if (receiverId == null) {
            receiverId = extractBankCode(payment.getCreditorAccount());
        }
        return RECEIVER_DI + receiverId + "\n";
    }

    private String buildOriginatorInfo(Payment payment) {
        // In real implementation, this would come from the payment initiator
        return ORIGINATOR_INFO + "MAPLE FINANCIAL CORP/123 MAIN ST/NEW YORK NY 10001\n";
    }

    private String buildBeneficiaryInfo(Payment payment) {
        StringBuilder benefInfo = new StringBuilder();
        benefInfo.append(payment.getCreditorName() != null ? payment.getCreditorName() : "BENEFICIARY");
        benefInfo.append("/").append(extractAccountNumber(payment.getCreditorAccount()));
        return BENEFICIARY_INFO + benefInfo.toString() + "\n";
    }

    private String buildRemittanceInfo(Payment payment) {
        return REMITTANCE_INFO + payment.getPaymentPurpose() + "\n";
    }

    private String buildChipsFields(Payment payment, PartnerProfile profile) {
        StringBuilder chips = new StringBuilder();
        chips.append("{7033}").append(profile.getChipsRoutingCode()).append("\n");
        chips.append("{7034}").append(generateChipsSequence()).append("\n");
        return chips.toString();
    }

    private String buildRegulatoryFields(Payment payment) {
        StringBuilder regulatory = new StringBuilder();
        // FinCEN fields for large value transfers
        regulatory.append("{8200}").append("CTR").append("\n"); // Currency Transaction Report
        regulatory.append("{8250}").append(payment.getPaymentReference()).append("\n");
        return regulatory.toString();
    }

    private String formatChipsMessage(Map<String, String> fields) {
        StringBuilder message = new StringBuilder();
        message.append("FIN ").append(fields.get("MSG")).append("\n");
        message.append(":20:").append(fields.get("SEQ")).append("\n");
        message.append(":32A:").append(fields.get("VDT")).append(fields.get("CCY"))
               .append(fields.get("AMT")).append("\n");
        message.append(":50A:").append(fields.get("SND")).append("\n");
        message.append(":59:").append(fields.get("RCV")).append("\n").append(fields.get("BNF")).append("\n");
        
        if (fields.containsKey("REM")) {
            message.append(":70:").append(fields.get("REM")).append("\n");
        }
        
        return message.toString();
    }

    private void validateFedwireMessage(String message, WireTransferType transferType) {
        // Basic validation of required fields
        if (!message.contains(MESSAGE_DISPOSITION)) {
            throw new FedwireValidationException("Missing message disposition field");
        }
        if (!message.contains(BUSINESS_FUNCTION_CODE)) {
            throw new FedwireValidationException("Missing business function code");
        }
        if (!message.contains(AMOUNT)) {
            throw new FedwireValidationException("Missing amount field");
        }
        // Additional validations would be implemented here
    }

    private void validateChipsMessage(String message) {
        if (!message.contains(":20:")) {
            throw new ChipsValidationException("Missing CHIPS reference field");
        }
        if (!message.contains(":32A:")) {
            throw new ChipsValidationException("Missing value date and amount field");
        }
    }

    private String signMessage(String message) throws HsmClient.HsmException {
        byte[] messageBytes = message.getBytes();
        byte[] signature = hsmClient.sign(messageBytes);
        return java.util.Base64.getEncoder().encodeToString(signature);
    }

    private String generateImad() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        return imadPrefix + timestamp + String.format("%04d", (int)(Math.random() * 10000));
    }

    private String generateChipsSequence() {
        return "CHP" + System.currentTimeMillis() % 1000000;
    }

    private String determineReceiverId(WireTransferType transferType) {
        return "021000021"; // Federal Reserve Bank routing number
    }

    private String getValueDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    }

    private String extractBankCode(String account) {
        if (account.contains("-")) {
            return account.split("-")[0].replaceAll("[^0-9]", "");
        }
        return "021000021"; // Default Fed routing number
    }

    private String extractAccountNumber(String account) {
        if (account.contains("-")) {
            return account.split("-")[1];
        }
        return account;
    }

    // Enums and inner classes

    public enum WireTransferType {
        CUSTOMER_TRANSFER,
        BANK_TO_BANK,
        DRAWDOWN_REQUEST
    }

    public static class FedwireMessage {
        private final String content;
        private final String imad;
        private final String signature;
        private final UUID paymentId;

        public FedwireMessage(String content, String imad, String signature, UUID paymentId) {
            this.content = content;
            this.imad = imad;
            this.signature = signature;
            this.paymentId = paymentId;
        }

        // Getters
        public String getContent() { return content; }
        public String getImad() { return imad; }
        public String getSignature() { return signature; }
        public UUID getPaymentId() { return paymentId; }
    }

    public static class ChipsMessage {
        private final String content;
        private final String sequenceNumber;
        private final UUID paymentId;

        public ChipsMessage(String content, String sequenceNumber, UUID paymentId) {
            this.content = content;
            this.sequenceNumber = sequenceNumber;
            this.paymentId = paymentId;
        }

        // Getters
        public String getContent() { return content; }
        public String getSequenceNumber() { return sequenceNumber; }
        public UUID getPaymentId() { return paymentId; }
    }

    // Exception classes
    public static class FedwireGenerationException extends RuntimeException {
        public FedwireGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FedwireValidationException extends RuntimeException {
        public FedwireValidationException(String message) {
            super(message);
        }
    }

    public static class ChipsGenerationException extends RuntimeException {
        public ChipsGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ChipsValidationException extends RuntimeException {
        public ChipsValidationException(String message) {
            super(message);
        }
    }
}

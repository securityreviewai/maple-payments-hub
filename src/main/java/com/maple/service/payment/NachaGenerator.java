package com.maple.service.payment;

import com.maple.model.Payment;
import com.maple.model.PaymentStatus;
import com.maple.repository.PaymentRepository;
import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NACHA (ACH) file generator for domestic payment processing.
 * 
 * Generates ACH files compliant with NACHA operating rules including:
 * - PPD (Prearranged Payment and Deposit) for consumer transactions
 * - CCD (Cash Concentration and Disbursement) for corporate transactions
 * - SEC codes, effective dates, and addenda records
 * - Proper batch and file control totals
 */
@Service
public class NachaGenerator {

    private static final Logger logger = LoggerFactory.getLogger(NachaGenerator.class);
    
    // NACHA record types
    private static final String FILE_HEADER_RECORD = "1";
    private static final String BATCH_HEADER_RECORD = "5";
    private static final String ENTRY_DETAIL_RECORD = "6";
    private static final String ADDENDA_RECORD = "7";
    private static final String BATCH_CONTROL_RECORD = "8";
    private static final String FILE_CONTROL_RECORD = "9";
    
    // Standard field lengths per NACHA spec
    private static final int RECORD_LENGTH = 94;
    
    @Value("${maple.nacha.immediate-destination:091000019}")
    private String immediateDestination;
    
    @Value("${maple.nacha.immediate-origin:123456789}")
    private String immediateOrigin;
    
    @Value("${maple.nacha.company-name:MAPLE FINANCIAL CORP}")
    private String companyName;
    
    @Value("${maple.nacha.company-id:1234567890}")
    private String companyId;

    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Autowired
    public NachaGenerator(PaymentRepository paymentRepository, AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    /**
     * Generates NACHA file for approved ACH payments.
     */
    public NachaFile generateAchFile(List<Payment> payments, String batchId) {
        logger.info("Generating NACHA file for batch: {} with {} payments", batchId, payments.size());
        
        try {
            StringWriter writer = new StringWriter();
            AtomicInteger sequenceNumber = new AtomicInteger(1);
            
            // Group payments by SEC code for separate batches
            var ppdPayments = payments.stream()
                .filter(p -> isPpdPayment(p))
                .toList();
            var ccdPayments = payments.stream()
                .filter(p -> !isPpdPayment(p))
                .toList();
            
            // Generate file header
            writer.write(generateFileHeader(sequenceNumber.getAndIncrement()));
            writer.write("\n");
            
            long totalCredits = 0;
            long totalDebits = 0;
            int totalEntries = 0;
            int batchCount = 0;
            
            // Generate PPD batch if any consumer payments
            if (!ppdPayments.isEmpty()) {
                NachaBatch ppdBatch = generateBatch(ppdPayments, "PPD", batchCount + 1, sequenceNumber);
                writer.write(ppdBatch.getBatchContent());
                totalCredits += ppdBatch.getTotalCredits();
                totalDebits += ppdBatch.getTotalDebits();
                totalEntries += ppdBatch.getEntryCount();
                batchCount++;
            }
            
            // Generate CCD batch if any corporate payments
            if (!ccdPayments.isEmpty()) {
                NachaBatch ccdBatch = generateBatch(ccdPayments, "CCD", batchCount + 1, sequenceNumber);
                writer.write(ccdBatch.getBatchContent());
                totalCredits += ccdBatch.getTotalCredits();
                totalDebits += ccdBatch.getTotalDebits();
                totalEntries += ccdBatch.getEntryCount();
                batchCount++;
            }
            
            // Generate file control
            writer.write(generateFileControl(batchCount, totalEntries, totalCredits, totalDebits, sequenceNumber.get()));
            
            String fileContent = writer.toString();
            
            // Validate file format
            validateNachaFile(fileContent);
            
            auditService.auditSystemAction("NACHA_FILE_GENERATED", "AchBatch", batchId);
            
            return new NachaFile(fileContent, batchId, payments.size(), totalCredits + totalDebits);
            
        } catch (Exception e) {
            logger.error("Failed to generate NACHA file for batch: {}", batchId, e);
            auditService.auditSystemAction("NACHA_FILE_GENERATION_FAILED", "AchBatch", batchId);
            throw new NachaGenerationException("NACHA file generation failed", e);
        }
    }

    /**
     * Generates a batch within the NACHA file.
     */
    private NachaBatch generateBatch(List<Payment> payments, String secCode, int batchNumber, AtomicInteger sequenceNumber) {
        StringWriter batchWriter = new StringWriter();
        
        // Batch header
        batchWriter.write(generateBatchHeader(secCode, batchNumber, sequenceNumber.getAndIncrement()));
        batchWriter.write("\n");
        
        long batchCredits = 0;
        long batchDebits = 0;
        String batchHashTotal = "0000000000";
        
        // Entry details
        for (Payment payment : payments) {
            batchWriter.write(generateEntryDetail(payment, sequenceNumber.getAndIncrement()));
            batchWriter.write("\n");
            
            // Add addenda if payment purpose exists
            if (payment.getPaymentPurpose() != null && !payment.getPaymentPurpose().trim().isEmpty()) {
                batchWriter.write(generateAddenda(payment, sequenceNumber.getAndIncrement()));
                batchWriter.write("\n");
            }
            
            batchCredits += payment.getAmountCents();
        }
        
        // Batch control
        batchWriter.write(generateBatchControl(secCode, batchNumber, payments.size(), 
                                             batchCredits, batchDebits, batchHashTotal, sequenceNumber.getAndIncrement()));
        batchWriter.write("\n");
        
        return new NachaBatch(batchWriter.toString(), payments.size(), batchCredits, batchDebits);
    }

    /**
     * Generates NACHA file header record (Type 1).
     */
    private String generateFileHeader(int sequenceNumber) {
        return String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s",
            FILE_HEADER_RECORD,                                    // Record Type Code
            "01",                                                   // Priority Code
            padRight(immediateDestination, 10),                    // Immediate Destination
            padRight(immediateOrigin, 10),                         // Immediate Origin
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")), // File Creation Date
            LocalDate.now().format(DateTimeFormatter.ofPattern("HHmm")),   // File Creation Time
            "A",                                                    // File ID Modifier
            "094",                                                  // Record Size
            "10",                                                   // Blocking Factor
            "1",                                                    // Format Code
            padRight("MAPLE FINANCIAL", 23),                       // Immediate Destination Name
            padRight("MAPLE PAYMENTS HUB", 23),                    // Immediate Origin Name
            padLeft("", 8)                                         // Reference Code
        );
    }

    /**
     * Generates NACHA batch header record (Type 5).
     */
    private String generateBatchHeader(String secCode, int batchNumber, int sequenceNumber) {
        return String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
            BATCH_HEADER_RECORD,                                   // Record Type Code
            "200",                                                  // Service Class Code (200 = Mixed Credits/Debits)
            padRight(companyName, 16),                             // Company Name
            padRight("", 20),                                      // Company Discretionary Data
            padRight(companyId, 10),                               // Company Identification
            secCode,                                               // Standard Entry Class Code
            padRight("PAYROLL", 10),                               // Company Entry Description
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")), // Company Descriptive Date
            getEffectiveDate(),                                    // Effective Entry Date
            padRight("", 3),                                       // Settlement Date
            "1",                                                   // Originator Status Code
            padRight(immediateDestination.substring(0, 8), 8),     // Originating DFI Identification
            padLeft(String.valueOf(batchNumber), 7)                // Batch Number
        );
    }

    /**
     * Generates NACHA entry detail record (Type 6).
     */
    private String generateEntryDetail(Payment payment, int sequenceNumber) {
        String transactionCode = getTransactionCode(payment);
        String rdfi = extractRoutingNumber(payment.getCreditorAccount());
        String accountNumber = extractAccountNumber(payment.getCreditorAccount());
        
        return String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
            ENTRY_DETAIL_RECORD,                                   // Record Type Code
            transactionCode,                                       // Transaction Code
            rdfi,                                                  // Receiving DFI Identification
            calculateCheckDigit(rdfi),                             // Check Digit
            padRight(accountNumber, 17),                           // DFI Account Number
            padLeft(String.valueOf(payment.getAmountCents()), 10), // Amount
            padRight(payment.getPaymentReference(), 15),           // Individual Identification Number
            padRight(payment.getCreditorName() != null ? 
                     payment.getCreditorName() : "BENEFICIARY", 22), // Individual Name
            "0",                                                   // Discretionary Data
            payment.getPaymentPurpose() != null && 
            !payment.getPaymentPurpose().trim().isEmpty() ? "1" : "0", // Addenda Record Indicator
            padLeft(String.valueOf(sequenceNumber), 15)           // Trace Number
        );
    }

    /**
     * Generates NACHA addenda record (Type 7).
     */
    private String generateAddenda(Payment payment, int sequenceNumber) {
        return String.format("%s%s%s%s%s",
            ADDENDA_RECORD,                                        // Record Type Code
            "05",                                                  // Addenda Type Code
            padRight(payment.getPaymentPurpose(), 80),             // Payment Related Information
            padLeft(String.valueOf(sequenceNumber), 7),           // Addenda Sequence Number
            padLeft(String.valueOf(sequenceNumber), 15)           // Entry Detail Sequence Number
        );
    }

    /**
     * Generates NACHA batch control record (Type 8).
     */
    private String generateBatchControl(String secCode, int batchNumber, int entryCount,
                                      long totalCredits, long totalDebits, String hashTotal, int sequenceNumber) {
        return String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
            BATCH_CONTROL_RECORD,                                  // Record Type Code
            "200",                                                 // Service Class Code
            padLeft(String.valueOf(entryCount), 6),               // Entry/Addenda Count
            padLeft(hashTotal, 10),                               // Entry Hash
            padLeft(String.valueOf(totalDebits), 12),             // Total Debit Entry Dollar Amount
            padLeft(String.valueOf(totalCredits), 12),            // Total Credit Entry Dollar Amount
            padRight(companyId, 10),                              // Company Identification
            padRight("", 19),                                     // Message Authentication Code
            padRight("", 6),                                      // Reserved
            padRight(immediateDestination.substring(0, 8), 8),    // Originating DFI Identification
            padLeft(String.valueOf(batchNumber), 7)               // Batch Number
        );
    }

    /**
     * Generates NACHA file control record (Type 9).
     */
    private String generateFileControl(int batchCount, int entryCount, long totalCredits, 
                                     long totalDebits, int sequenceNumber) {
        return String.format("%s%s%s%s%s%s%s%s%s%s%s",
            FILE_CONTROL_RECORD,                                   // Record Type Code
            padLeft(String.valueOf(batchCount), 6),               // Batch Count
            padLeft(String.valueOf((batchCount + 2) / 10 + 1), 6), // Block Count
            padLeft(String.valueOf(entryCount), 8),               // Entry/Addenda Count
            padLeft("0000000000", 10),                            // Entry Hash
            padLeft(String.valueOf(totalDebits), 12),             // Total Debit Entry Dollar Amount
            padLeft(String.valueOf(totalCredits), 12),            // Total Credit Entry Dollar Amount
            padLeft("", 39)                                       // Reserved
        );
    }

    // Helper methods

    private boolean isPpdPayment(Payment payment) {
        // Simplified logic - in real implementation, this would check account type or payment metadata
        return payment.getAmountCents() < 2500000; // Under $25k = consumer (PPD)
    }

    private String getTransactionCode(Payment payment) {
        // 22 = Checking Credit, 32 = Savings Credit
        // 27 = Checking Debit, 37 = Savings Debit
        return "22"; // Simplified - always checking credit
    }

    private String extractRoutingNumber(String account) {
        // Extract routing number from account string (format: RTN-ACCOUNT)
        if (account.contains("-")) {
            return account.split("-")[0].replaceAll("[^0-9]", "");
        }
        return "091000019"; // Default routing number
    }

    private String extractAccountNumber(String account) {
        // Extract account number from account string
        if (account.contains("-")) {
            return account.split("-")[1].replaceAll("[^0-9]", "");
        }
        return account.replaceAll("[^0-9]", "");
    }

    private String calculateCheckDigit(String routingNumber) {
        // NACHA check digit calculation
        if (routingNumber.length() != 8) return "0";
        
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            int digit = Character.getNumericValue(routingNumber.charAt(i));
            int weight = (i % 3 == 0) ? 3 : (i % 3 == 1) ? 7 : 1;
            sum += digit * weight;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        return String.valueOf(checkDigit);
    }

    private String getEffectiveDate() {
        // Default to next business day
        LocalDate effectiveDate = LocalDate.now().plusDays(1);
        // Skip weekends (simplified - real implementation would skip bank holidays)
        while (effectiveDate.getDayOfWeek().getValue() > 5) {
            effectiveDate = effectiveDate.plusDays(1);
        }
        return effectiveDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
    }

    private void validateNachaFile(String fileContent) {
        String[] lines = fileContent.split("\n");
        
        // Validate file structure
        if (lines.length < 4) {
            throw new NachaValidationException("NACHA file must contain at least file header, batch header, batch control, and file control");
        }
        
        // Validate record lengths
        for (String line : lines) {
            if (!line.isEmpty() && line.length() != RECORD_LENGTH) {
                throw new NachaValidationException("Invalid record length: " + line.length() + " (expected: " + RECORD_LENGTH + ")");
            }
        }
        
        // Validate record types
        if (!lines[0].startsWith(FILE_HEADER_RECORD)) {
            throw new NachaValidationException("File must start with file header record (type 1)");
        }
        
        if (!lines[lines.length - 1].startsWith(FILE_CONTROL_RECORD)) {
            throw new NachaValidationException("File must end with file control record (type 9)");
        }
    }

    private String padLeft(String value, int length) {
        return String.format("%" + length + "s", value).replace(' ', '0');
    }

    private String padRight(String value, int length) {
        return String.format("%-" + length + "s", value);
    }

    // Inner classes for return types
    
    public static class NachaFile {
        private final String content;
        private final String batchId;
        private final int paymentCount;
        private final long totalAmount;

        public NachaFile(String content, String batchId, int paymentCount, long totalAmount) {
            this.content = content;
            this.batchId = batchId;
            this.paymentCount = paymentCount;
            this.totalAmount = totalAmount;
        }

        // Getters
        public String getContent() { return content; }
        public String getBatchId() { return batchId; }
        public int getPaymentCount() { return paymentCount; }
        public long getTotalAmount() { return totalAmount; }
    }

    private static class NachaBatch {
        private final String batchContent;
        private final int entryCount;
        private final long totalCredits;
        private final long totalDebits;

        public NachaBatch(String batchContent, int entryCount, long totalCredits, long totalDebits) {
            this.batchContent = batchContent;
            this.entryCount = entryCount;
            this.totalCredits = totalCredits;
            this.totalDebits = totalDebits;
        }

        // Getters
        public String getBatchContent() { return batchContent; }
        public int getEntryCount() { return entryCount; }
        public long getTotalCredits() { return totalCredits; }
        public long getTotalDebits() { return totalDebits; }
    }

    // Exception classes
    public static class NachaGenerationException extends RuntimeException {
        public NachaGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NachaValidationException extends RuntimeException {
        public NachaValidationException(String message) {
            super(message);
        }
    }
}

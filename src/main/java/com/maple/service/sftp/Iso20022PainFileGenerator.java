package com.maple.service.sftp;

import com.maple.model.Payment;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a minimal ISO 20022 pain.001-style XML payload for batch SFTP delivery.
 * Not a full schema-valid implementation; suitable for integration testing and partner handoff.
 */
public final class Iso20022PainFileGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private Iso20022PainFileGenerator() {}

    public static String buildFilename(String batchReference) {
        String safe = batchReference.replaceAll("[^A-Za-z0-9._-]", "_");
        return "pain.001.001.03-" + safe + ".xml";
    }

    public static byte[] buildDocument(String batchReference, List<Payment> payments) {
        String msgId = "MSG-" + UUID.randomUUID();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">\n");
        sb.append("  <CstmrCdtTrfInitn>\n");
        sb.append("    <GrpHdr>\n");
        sb.append("      <MsgId>").append(escape(msgId)).append("</MsgId>\n");
        sb.append("      <CreDtTm>").append(escape(TS.format(OffsetDateTime.now()))).append("</CreDtTm>\n");
        sb.append("      <NbOfTxs>").append(payments.size()).append("</NbOfTxs>\n");
        sb.append("      <CtrlSum>")
                .append(payments.stream().mapToLong(Payment::getAmountCents).sum() / 100.0)
                .append("</CtrlSum>\n");
        sb.append("      <InitgPty><Nm>MaplePaymentsHub</Nm></InitgPty>\n");
        sb.append("    </GrpHdr>\n");
        sb.append("    <PmtInf>\n");
        sb.append("      <PmtInfId>").append(escape(batchReference)).append("</PmtInfId>\n");
        sb.append("      <PmtMtd>TRF</PmtMtd>\n");
        sb.append("      <ReqdExctnDt>")
                .append(escape(DateTimeFormatter.ISO_LOCAL_DATE.format(OffsetDateTime.now().toLocalDate())))
                .append("</ReqdExctnDt>\n");
        int idx = 1;
        for (Payment p : payments) {
            sb.append("      <CdtTrfTxInf>\n");
            sb.append("        <PmtId><EndToEndId>")
                    .append(escape(p.getPaymentReference()))
                    .append("</EndToEndId></PmtId>\n");
            sb.append("        <Amt><InstdAmt Ccy=\"")
                    .append(escape(Objects.toString(p.getCurrency(), "USD")))
                    .append("\">")
                    .append(p.getAmountCents() / 100.0)
                    .append("</InstdAmt></Amt>\n");
            sb.append("        <Cdtr><Nm>")
                    .append(escape(Optional.ofNullable(p.getCreditorName()).orElse("")))
                    .append("</Nm></Cdtr>\n");
            sb.append("        <CdtrAcct><Id><Othr><Id>")
                    .append(escape(p.getCreditorAccount()))
                    .append("</Id></Othr></Id></CdtrAcct>\n");
            sb.append("        <DbtrAcct><Id><Othr><Id>")
                    .append(escape(p.getDebtorAccount()))
                    .append("</Id></Othr></Id></DbtrAcct>\n");
            sb.append("        <RmtInf><Ustrd>")
                    .append(escape(Optional.ofNullable(p.getPaymentPurpose()).orElse("")))
                    .append("</Ustrd></RmtInf>\n");
            sb.append("        <TxIndex>").append(idx++).append("</TxIndex>\n");
            sb.append("      </CdtTrfTxInf>\n");
        }
        sb.append("    </PmtInf>\n");
        sb.append("  </CstmrCdtTrfInitn>\n");
        sb.append("</Document>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

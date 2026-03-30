package com.maple.service.venmo;

import com.maple.dto.VenmoCardImportRequestDto;
import com.maple.dto.VenmoCardImportResponseDto;
import com.maple.dto.VenmoCardVisualizationDto;
import com.maple.model.VenmoCardEntry;
import com.maple.repository.VenmoCardEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Venmo-sourced card/transaction import and visualization.
 * Data is user-scoped; no raw card numbers are stored or returned.
 */
@Service
public class VenmoCardService {

    private static final Logger logger = LoggerFactory.getLogger(VenmoCardService.class);
    private static final int MAX_AMOUNT_CENTS = 999_999_99; // ~$1M
    private static final int MIN_AMOUNT_CENTS = -MAX_AMOUNT_CENTS;
    private static final int MAX_DAYS_IN_PAST = 365 * 2; // 2 years

    private final VenmoCardEntryRepository venmoCardEntryRepository;

    public VenmoCardService(VenmoCardEntryRepository venmoCardEntryRepository) {
        this.venmoCardEntryRepository = venmoCardEntryRepository;
    }

    /**
     * Imports Venmo-sourced card/transaction entries for the user.
     * Validates dates, amounts, and list size; rejects invalid entries.
     */
    @Transactional
    public VenmoCardImportResponseDto importCardData(UUID userId, VenmoCardImportRequestDto request) {
        LocalDate cutoff = LocalDate.now().minusDays(MAX_DAYS_IN_PAST);
        int accepted = 0;
        for (var entry : request.getEntries()) {
            if (isValidEntry(entry, cutoff)) {
                VenmoCardEntry entity = new VenmoCardEntry(
                        userId,
                        entry.getTransactionDate(),
                        clampAmount(entry.getAmountCents()),
                        entry.getDescription(),
                        normalizeType(entry.getEntryType())
                );
                venmoCardEntryRepository.save(entity);
                accepted++;
            }
        }
        String importId = "venmo-card-imp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        logger.info("Venmo card import userId={} importId={} acceptedCount={} totalCount={}",
                userId, importId, accepted, request.getEntries().size());
        return VenmoCardImportResponseDto.builder()
                .importId(importId)
                .status("accepted")
                .acceptedCount(accepted)
                .build();
    }

    /**
     * Returns chart-ready visualization data for the user's Venmo card data in the period.
     * Date range limited to 365 days.
     */
    @Transactional(readOnly = true)
    public VenmoCardVisualizationDto getVisualization(UUID userId, LocalDate start, LocalDate end) {
        LocalDate endCap = LocalDate.now();
        LocalDate startCap = endCap.minusDays(365);
        LocalDate s = start == null ? startCap : (start.isBefore(startCap) ? startCap : start);
        LocalDate e = end == null ? endCap : (end.isAfter(endCap) ? endCap : end);
        if (s.isAfter(e)) {
            s = e.minusDays(30);
        }

        List<Object[]> volumeByDay = venmoCardEntryRepository.findUserVolumeByDay(userId, s, e);
        List<VenmoCardVisualizationDto.VolumeAtDateDto> volumeOverTime = volumeByDay.stream()
                .map(row -> VenmoCardVisualizationDto.VolumeAtDateDto.builder()
                        .date((String) row[0])
                        .volumeCents(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        List<VenmoCardEntry> entries = venmoCardEntryRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(userId, s, e);
        long totalSpendCents = entries.stream().mapToLong(e1 -> Math.abs(e1.getAmountCents())).sum();
        Map<String, Long> volumeByType = new LinkedHashMap<>();
        for (VenmoCardEntry ent : entries) {
            String type = ent.getEntryType() != null ? ent.getEntryType() : "payment";
            volumeByType.merge(type, Math.abs(ent.getAmountCents()), Long::sum);
        }

        return VenmoCardVisualizationDto.builder()
                .volumeOverTime(volumeOverTime)
                .totalSpendCents(totalSpendCents)
                .transactionCount((long) entries.size())
                .volumeByType(volumeByType)
                .build();
    }

    private boolean isValidEntry(com.maple.dto.VenmoCardImportEntryDto entry, LocalDate cutoff) {
        if (entry == null || entry.getTransactionDate() == null || entry.getAmountCents() == null) {
            return false;
        }
        if (entry.getAmountCents() == 0) {
            return false;
        }
        if (entry.getAmountCents() < MIN_AMOUNT_CENTS || entry.getAmountCents() > MAX_AMOUNT_CENTS) {
            return false;
        }
        if (entry.getTransactionDate().isAfter(LocalDate.now()) || entry.getTransactionDate().isBefore(cutoff)) {
            return false;
        }
        return true;
    }

    private static long clampAmount(Long amountCents) {
        if (amountCents == null) return 0;
        return Math.max(MIN_AMOUNT_CENTS, Math.min(MAX_AMOUNT_CENTS, amountCents));
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "payment";
        String t = type.strip();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}

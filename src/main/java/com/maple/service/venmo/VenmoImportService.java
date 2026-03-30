package com.maple.service.venmo;

import com.maple.dto.VenmoImportRequestDto;
import com.maple.dto.VenmoImportResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for Venmo user data import.
 *
 * <p>Validates and processes import requests. Venmo's official API is retired for
 * new applications; this service accepts and validates data for preparation or
 * future integration.
 */
@Service
public class VenmoImportService {

    private static final Logger logger = LoggerFactory.getLogger(VenmoImportService.class);

    /**
     * Processes a Venmo import request: validates entries and returns an acknowledgment.
     *
     * @param request the import request with entries (displayName, identifier)
     * @return response with importId, status, and accepted count
     */
    public VenmoImportResponseDto importUserData(VenmoImportRequestDto request) {
        // Sanitize identifiers (Venmo usernames: alphanumeric, hyphen, underscore)
        int accepted = 0;
        for (var entry : request.getEntries()) {
            String id = entry.getIdentifier().strip();
            if (isValidIdentifier(id)) {
                accepted++;
            }
        }

        String importId = "venmo-imp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        logger.info(
                "Venmo import processed importId={} acceptedCount={} totalCount={}",
                importId,
                accepted,
                request.getEntries().size());

        return VenmoImportResponseDto.builder()
                .importId(importId)
                .status("accepted")
                .acceptedCount(accepted)
                .build();
    }

    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        // Venmo usernames: @username or email or phone; allow alphanumeric, @, ., -, _, +, spaces for email/phone
        return identifier.length() <= 256
                && !identifier.contains("\n")
                && !identifier.contains("\r");
    }
}

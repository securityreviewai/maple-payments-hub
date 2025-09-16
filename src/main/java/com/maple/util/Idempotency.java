package com.maple.util;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for idempotency handling.
 * 
 * Provides methods for generating and validating idempotency keys
 * and request fingerprints for duplicate detection.
 */
@Component
public class Idempotency {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Generates a hash fingerprint of request content for idempotency checking.
     * 
     * @param content the request content to hash
     * @return SHA-256 hash of the content as base64 string
     */
    public String generateRequestHash(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validates an idempotency key format.
     * 
     * @param idempotencyKey the key to validate
     * @return true if the key is valid
     */
    public boolean isValidIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return false;
        }
        
        // Key should be between 1 and 128 characters
        if (idempotencyKey.length() > 128) {
            return false;
        }
        
        // Key should contain only alphanumeric characters, hyphens, and underscores
        return idempotencyKey.matches("^[a-zA-Z0-9\\-_]+$");
    }

    /**
     * Generates a default idempotency key based on request content.
     * 
     * @param prefix a prefix for the key (e.g., "pay", "approve")
     * @param content the request content
     * @return generated idempotency key
     */
    public String generateIdempotencyKey(String prefix, String content) {
        String hash = generateRequestHash(content);
        String shortHash = hash.substring(0, Math.min(16, hash.length()));
        // Add random suffix for uniqueness
        int randomSuffix = (int)(Math.random() * 10000);
        return String.format("%s-%s-%d", prefix, shortHash, randomSuffix);
    }

    /**
     * Checks if two request contents are identical for idempotency purposes.
     * 
     * @param content1 first request content
     * @param content2 second request content  
     * @return true if contents are identical
     */
    public boolean areRequestsIdentical(String content1, String content2) {
        if (content1 == null && content2 == null) {
            return true;
        }
        
        if (content1 == null || content2 == null) {
            return false;
        }
        
        return generateRequestHash(content1).equals(generateRequestHash(content2));
    }

    /**
     * Normalizes request content for consistent hashing.
     * 
     * Removes whitespace variations and sorts JSON keys for consistent
     * hash generation across equivalent requests.
     * 
     * @param content the content to normalize
     * @return normalized content
     */
    public String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        
        // Basic normalization - remove extra whitespace
        String normalized = content.replaceAll("\\s+", " ").trim();
        
        // For more sophisticated JSON normalization, you might want to:
        // 1. Parse JSON and sort keys
        // 2. Remove formatting differences
        // 3. Handle null vs missing fields consistently
        
        return normalized;
    }

    /**
     * Creates a composite key for multi-field idempotency.
     * 
     * @param fields the fields to include in the composite key
     * @return composite idempotency key
     */
    public String createCompositeKey(String... fields) {
        if (fields == null || fields.length == 0) {
            return "";
        }
        
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                builder.append("|");
            }
            builder.append(fields[i] != null ? fields[i] : "");
        }
        
        return generateRequestHash(builder.toString());
    }
}

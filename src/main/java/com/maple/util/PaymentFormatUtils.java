package com.maple.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility class for formatting payment amounts and related data.
 * 
 * Provides consistent formatting across the application for amounts,
 * currencies, and related payment display information.
 */
public class PaymentFormatUtils {

    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("#,##0.00");

    /**
     * Formats an amount in cents to a currency string.
     * 
     * @param amountCents Amount in cents
     * @param currency Currency code (e.g., "USD", "EUR")
     * @return Formatted string like "1,500.00 USD"
     */
    public static String formatAmount(Long amountCents, String currency) {
        if (amountCents == null) {
            return "0.00 " + (currency != null ? currency : "USD");
        }
        
        double amount = amountCents / 100.0;
        return String.format("%s %s", DECIMAL_FORMATTER.format(amount), currency != null ? currency : "USD");
    }

    /**
     * Formats an amount in cents to a currency string with symbol.
     * 
     * @param amountCents Amount in cents
     * @param currency Currency code
     * @return Formatted string like "$1,500.00" (for USD)
     */
    public static String formatAmountWithSymbol(Long amountCents, String currency) {
        if (amountCents == null) {
            return "$0.00";
        }
        
        double amount = amountCents / 100.0;
        
        // For USD, use standard currency formatter
        if ("USD".equalsIgnoreCase(currency)) {
            return CURRENCY_FORMATTER.format(amount);
        }
        
        // For other currencies, use standard format
        return formatAmount(amountCents, currency);
    }

    /**
     * Formats an amount to a compact representation.
     * 
     * @param amountCents Amount in cents
     * @return Compact format like "1.5K" or "1.2M"
     */
    public static String formatAmountCompact(Long amountCents) {
        if (amountCents == null) {
            return "0";
        }
        
        double amount = amountCents / 100.0;
        
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000);
        } else {
            return String.format("%.2f", amount);
        }
    }

    /**
     * Formats a percentage value.
     * 
     * @param percentage Percentage value (0-100)
     * @return Formatted string like "85.5%"
     */
    public static String formatPercentage(Double percentage) {
        if (percentage == null) {
            return "0.0%";
        }
        return String.format("%.2f%%", percentage);
    }

    /**
     * Masks an account number showing only last 4 digits.
     * 
     * @param account Account number or identifier
     * @return Masked account like "****1234"
     */
    public static String maskAccount(String account) {
        if (account == null || account.length() <= 4) {
            return account != null ? account : "****";
        }
        return "****" + account.substring(account.length() - 4);
    }

    /**
     * Masks an account number with custom masking character.
     * 
     * @param account Account number
     * @param maskChar Character to use for masking
     * @param visibleChars Number of visible characters at the end
     * @return Masked account
     */
    public static String maskAccount(String account, char maskChar, int visibleChars) {
        if (account == null || account.length() <= visibleChars) {
            return account != null ? account : String.valueOf(maskChar).repeat(4);
        }
        
        int maskLength = account.length() - visibleChars;
        return String.valueOf(maskChar).repeat(maskLength) + account.substring(account.length() - visibleChars);
    }

    /**
     * Formats a duration in minutes to a human-readable string.
     * 
     * @param minutes Duration in minutes
     * @return Formatted string like "2h 30m" or "45m"
     */
    public static String formatDuration(Long minutes) {
        if (minutes == null || minutes <= 0) {
            return "0m";
        }
        
        if (minutes < 60) {
            return minutes + "m";
        }
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        if (remainingMinutes == 0) {
            return hours + "h";
        }
        
        return String.format("%dh %dm", hours, remainingMinutes);
    }

    /**
     * Formats a payment reference for display.
     * 
     * @param paymentReference Payment reference
     * @return Formatted reference with proper spacing if applicable
     */
    public static String formatPaymentReference(String paymentReference) {
        if (paymentReference == null) {
            return "";
        }
        
        // Add spacing for readability if it's a long reference
        if (paymentReference.length() > 12 && paymentReference.contains("-")) {
            return paymentReference.replaceAll("-", " - ");
        }
        
        return paymentReference;
    }
}


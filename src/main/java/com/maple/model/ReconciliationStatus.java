package com.maple.model;

/**
 * Status of a bank statement entry in the reconciliation process.
 */
public enum ReconciliationStatus {

    /** Entry has been uploaded but matching has not yet been attempted. */
    PENDING,

    /** Exact match found against an internal payment (HIGH confidence). */
    MATCHED,

    /** Fuzzy match found against a single candidate payment (MEDIUM confidence); requires review. */
    PARTIAL_MATCH,

    /** No matching internal payment found. */
    UNMATCHED,

    /** Multiple candidate payments match; cannot automatically select one. */
    DUPLICATE,

    /** Operator manually linked this entry to a payment. */
    MANUALLY_MATCHED;

    /** Returns true when the entry no longer needs automated processing. */
    public boolean isResolved() {
        return this == MATCHED || this == MANUALLY_MATCHED || this == UNMATCHED;
    }

    /** Returns true when the entry has a confirmed payment link. */
    public boolean isLinked() {
        return this == MATCHED || this == MANUALLY_MATCHED || this == PARTIAL_MATCH;
    }
}

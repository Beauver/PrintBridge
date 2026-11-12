package com.beauver.printbridge.printrequest;

/**
 * Lifecycle of a print request: SUBMITTED → IN_REVIEW → ACCEPTED / DECLINED → COMPLETED.
 * DECLINED and COMPLETED are final. Only SUBMITTED is used until the review feature (#5).
 */
public enum RequestStatus {
    SUBMITTED,
    IN_REVIEW,
    ACCEPTED,
    DECLINED,
    COMPLETED
}

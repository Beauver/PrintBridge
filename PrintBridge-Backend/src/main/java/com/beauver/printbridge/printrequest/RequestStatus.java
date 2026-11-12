package com.beauver.printbridge.printrequest;

import java.util.List;
import java.util.Set;

/**
 * Lifecycle of a print request. DECLINED and COMPLETED are final: nothing on the request
 * changes after that.
 *
 * <pre>
 * SUBMITTED → IN_REVIEW, ACCEPTED, DECLINED
 * IN_REVIEW → ACCEPTED, DECLINED
 * ACCEPTED  → COMPLETED
 * </pre>
 */
public enum RequestStatus {
    SUBMITTED,
    IN_REVIEW,
    ACCEPTED,
    DECLINED,
    COMPLETED;

    /** Statuses that still need work from the admin. */
    public static final Set<RequestStatus> OPEN = Set.of(SUBMITTED, IN_REVIEW, ACCEPTED);

    /** Statuses the admin may move a request to from this one, in display order. */
    public List<RequestStatus> nextStatuses() {
        return switch (this) {
            case SUBMITTED -> List.of(IN_REVIEW, ACCEPTED, DECLINED);
            case IN_REVIEW -> List.of(ACCEPTED, DECLINED);
            case ACCEPTED -> List.of(COMPLETED);
            case DECLINED, COMPLETED -> List.of();
        };
    }

    public boolean canMoveTo(RequestStatus target) {
        return nextStatuses().contains(target);
    }

    public boolean isFinal() {
        return nextStatuses().isEmpty();
    }

    /**
     * Whether the request may be marked (un)paid. COMPLETED is included because people often pay
     * at pickup; it's the only change still allowed on a final request.
     */
    public boolean allowsPayment() {
        return this == ACCEPTED || this == COMPLETED;
    }
}

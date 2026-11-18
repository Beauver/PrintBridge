package com.beauver.printbridge.printrequest.file;

import java.util.List;

/**
 * Status of one file or link of a request (#61), set by the admin. The request's own status
 * changes move its items along; the items never change the request.
 *
 * <pre>
 * PENDING   → ACCEPTED, DECLINED
 * ACCEPTED  → PRINTING, COMPLETED, DECLINED
 * PRINTING  → COMPLETED, ACCEPTED, DECLINED
 * DECLINED  → ACCEPTED (reopened)
 * COMPLETED → ACCEPTED (reopened)
 * </pre>
 * Items only change while their request isn't final.
 */
public enum ItemStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    PRINTING,
    COMPLETED;

    /** Statuses the admin may move an item to from this one, in display order. */
    public List<ItemStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> List.of(ACCEPTED, DECLINED);
            case ACCEPTED -> List.of(PRINTING, COMPLETED, DECLINED);
            case PRINTING -> List.of(COMPLETED, ACCEPTED, DECLINED);
            case DECLINED, COMPLETED -> List.of(ACCEPTED);
        };
    }

    public boolean canMoveTo(ItemStatus target) {
        return nextStatuses().contains(target);
    }
}

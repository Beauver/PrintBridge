package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.common.error.InvalidInputException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** The status filter of the admin overview: {@code OPEN} (default), {@code ALL} or one status. */
public final class StatusFilter {

    public static final String OPEN = "OPEN";
    public static final String ALL = "ALL";

    private StatusFilter() {
    }

    /** The statuses a filter value stands for. Blank means {@code OPEN}. */
    public static Set<RequestStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return RequestStatus.OPEN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals(OPEN)) {
            return RequestStatus.OPEN;
        }
        if (normalized.equals(ALL)) {
            return EnumSet.allOf(RequestStatus.class);
        }
        try {
            return Set.of(RequestStatus.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown status filter: " + value);
        }
    }
}

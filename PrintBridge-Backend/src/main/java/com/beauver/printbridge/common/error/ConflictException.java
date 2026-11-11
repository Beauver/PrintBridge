package com.beauver.printbridge.common.error;

/** The request clashes with the current state (duplicate, not allowed in this state, ...). Maps to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

package com.beauver.printbridge.common.error;

/** Input that passed Bean Validation but fails a business rule (e.g. an invalid upload). Maps to 400. */
public class InvalidInputException extends DomainException {
    public InvalidInputException(String message) {
        super(message);
    }
}

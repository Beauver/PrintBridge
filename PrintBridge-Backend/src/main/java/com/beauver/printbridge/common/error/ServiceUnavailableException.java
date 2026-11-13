package com.beauver.printbridge.common.error;

/** A service PrintBridge depends on (like the slicer) can't be used right now. Maps to 503. */
public class ServiceUnavailableException extends DomainException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}

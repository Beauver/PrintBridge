package com.beauver.printbridge.common.error;

/** The resource does not exist or the caller may not know it exists. Maps to 404. */
public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

package com.beauver.printbridge.common.error;

/**
 * Base for expected failures that services report and {@link ExceptionMappers} turns into HTTP
 * responses. They are normal control flow, not bugs, so no stack trace is captured: that keeps
 * them about as cheap as returning a value.
 */
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message, null, false, false);
    }
}

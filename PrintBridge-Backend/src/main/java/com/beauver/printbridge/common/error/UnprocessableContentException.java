package com.beauver.printbridge.common.error;

/** The request is fine, but the content it points at can't be processed (e.g. a model without a preview). Maps to 422. */
public class UnprocessableContentException extends DomainException {
    public UnprocessableContentException(String message) {
        super(message);
    }
}

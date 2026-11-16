package com.beauver.printbridge.common.error;

import java.time.Duration;

/** A rate limit was passed; answered with 429 and {@code Retry-After}. */
public class TooManyRequestsException extends DomainException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(Duration retryAfter) {
        this(secondsRoundedUp(retryAfter));
    }

    private TooManyRequestsException(long retryAfterSeconds) {
        super("Too many attempts, try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** At least one second, so the client never retries straight away. */
    private static long secondsRoundedUp(Duration duration) {
        return Math.max(1, duration.plusMillis(999).toSeconds());
    }
}

package com.beauver.printbridge.common.ratelimit;

/** What a {@link RateLimit} counts requests by. */
public enum RateLimitKey {

    /** The client's address. For public endpoints. */
    IP,

    /** The logged-in user, whatever address they come from; the address for anonymous requests. */
    USER
}

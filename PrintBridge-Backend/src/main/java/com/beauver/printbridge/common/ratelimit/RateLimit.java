package com.beauver.printbridge.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits how often a REST endpoint may be called: at most {@link #limit} requests per
 * {@link #windowSeconds}, counted per client ({@link #per}). A request over the limit is answered
 * with 429 and a {@code Retry-After} header before the endpoint runs; every request counts,
 * also the ones that fail.
 * <p>
 * On a method it applies to that endpoint; on a resource class to each of its endpoints
 * separately, unless the method has its own. Counters live in memory (see {@link RateLimiter}).
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Requests allowed per window. */
    int limit();

    /** Length of the window in seconds; it starts with the first counted request. */
    int windowSeconds();

    RateLimitKey per() default RateLimitKey.IP;
}

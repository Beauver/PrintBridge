package com.beauver.printbridge.common.ratelimit;

import com.beauver.printbridge.common.error.TooManyRequestsException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ResourceInfo;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

/** Enforces {@link RateLimit} before the endpoint (and the validation of its input) runs. */
public class RateLimitFilter {

    @Inject
    RateLimiter limiter;

    @Inject
    ClientAddress clientAddress;

    @Inject
    SecurityIdentity identity;

    @ServerRequestFilter
    public void enforce(ResourceInfo resource) {
        Method method = resource.getResourceMethod();
        if (method == null) {
            return;
        }
        RateLimit limit = method.getAnnotation(RateLimit.class);
        if (limit == null) {
            limit = resource.getResourceClass().getAnnotation(RateLimit.class);
        }
        if (limit == null) {
            return;
        }
        String key = resource.getResourceClass().getName() + "#" + method.getName() + "|" + client(limit.per());
        Optional<Duration> retryAfter = limiter.acquire(key, limit.limit(), Duration.ofSeconds(limit.windowSeconds()));
        if (retryAfter.isPresent()) {
            throw new TooManyRequestsException(retryAfter.get());
        }
    }

    private String client(RateLimitKey per) {
        if (per == RateLimitKey.USER && !identity.isAnonymous()) {
            return "user:" + identity.getPrincipal().getName();
        }
        return "ip:" + clientAddress.get();
    }
}

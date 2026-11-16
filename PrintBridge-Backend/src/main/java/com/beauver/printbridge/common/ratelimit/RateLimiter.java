package com.beauver.printbridge.common.ratelimit;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key in fixed windows: a window starts with the first event for a key and
 * everything in it counts until it ends.
 * <p>
 * The counters are kept in memory. That fits a single backend instance: they are lost on a
 * restart and not shared between instances.
 */
@ApplicationScoped
public class RateLimiter {

    /** More keys than this are not tracked; see {@link #makeRoom}. */
    static final int MAX_KEYS = 100_000;

    private record Window(Instant end, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @ConfigProperty(name = "printbridge.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    Clock clock = Clock.systemUTC();

    public RateLimiter() {
    }

    /** A limiter that is switched on, with its own clock; for tests of code that uses one. */
    public RateLimiter(Clock clock) {
        this.enabled = true;
        this.clock = clock;
    }

    /**
     * Counts one event and checks the limit in one step.
     *
     * @return empty when the event is within the limit, else how long until the window ends
     */
    public Optional<Duration> acquire(String key, int limit, Duration window) {
        if (!enabled) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Window updated = count(key, window, now);
        if (updated.count() > limit) {
            return Optional.of(Duration.between(now, updated.end()));
        }
        return Optional.empty();
    }

    /**
     * Whether the key has used up its limit, without counting anything. For limits on outcomes
     * (failed logins): check first, {@link #record} once the outcome is known.
     *
     * @return empty when another attempt is allowed, else how long until the window ends
     */
    public Optional<Duration> blockedFor(String key, int limit) {
        if (!enabled) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Window current = windows.get(key);
        if (current == null || !current.end().isAfter(now) || current.count() < limit) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.end()));
    }

    /** Counts one event for the key. */
    public void record(String key, Duration window) {
        if (enabled) {
            count(key, window, clock.instant());
        }
    }

    /** Forgets the key's count. */
    public void reset(String key) {
        windows.remove(key);
    }

    private Window count(String key, Duration window, Instant now) {
        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
            makeRoom(now);
        }
        return windows.merge(key, new Window(now.plus(window), 1), (current, first) -> {
            if (!current.end().isAfter(now)) {
                return first;
            }
            return new Window(current.end(), current.count() + 1);
        });
    }

    /**
     * Keeps the map from growing without bound: drops windows that have ended, and if that isn't
     * enough, all of them. Losing the counts lets some requests through; refusing new clients
     * instead would let whoever filled the map lock everybody out.
     */
    private void makeRoom(Instant now) {
        windows.values().removeIf(window -> !window.end().isAfter(now));
        if (windows.size() >= MAX_KEYS) {
            Log.warnf("Rate limiter tracks more than %d keys; counters were reset", MAX_KEYS);
            windows.clear();
        }
    }

    int trackedKeys() {
        return windows.size();
    }
}

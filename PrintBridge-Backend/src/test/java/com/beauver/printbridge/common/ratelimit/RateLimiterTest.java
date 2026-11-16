package com.beauver.printbridge.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    static final Duration MINUTE = Duration.ofMinutes(1);

    final MutableClock clock = new MutableClock();
    final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenSaysHowLongToWait() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.acquire("a", 3, MINUTE).isEmpty());
        }
        clock.advance(Duration.ofSeconds(20));

        assertEquals(Optional.of(Duration.ofSeconds(40)), limiter.acquire("a", 3, MINUTE));
    }

    @Test
    void countsEachKeyOnItsOwn() {
        assertTrue(limiter.acquire("a", 1, MINUTE).isEmpty());
        assertTrue(limiter.acquire("b", 1, MINUTE).isEmpty());
        assertTrue(limiter.acquire("a", 1, MINUTE).isPresent());
    }

    @Test
    void startsAgainWhenTheWindowHasEnded() {
        assertTrue(limiter.acquire("a", 1, MINUTE).isEmpty());
        assertTrue(limiter.acquire("a", 1, MINUTE).isPresent());
        clock.advance(MINUTE);

        assertTrue(limiter.acquire("a", 1, MINUTE).isEmpty());
        assertTrue(limiter.acquire("a", 1, MINUTE).isPresent());
    }

    @Test
    void blockedForChecksWithoutCounting() {
        assertTrue(limiter.blockedFor("a", 2).isEmpty());
        limiter.record("a", MINUTE);
        assertTrue(limiter.blockedFor("a", 2).isEmpty());
        assertTrue(limiter.blockedFor("a", 2).isEmpty());
        limiter.record("a", MINUTE);
        clock.advance(Duration.ofSeconds(15));

        assertEquals(Optional.of(Duration.ofSeconds(45)), limiter.blockedFor("a", 2));
        clock.advance(Duration.ofSeconds(45));
        assertTrue(limiter.blockedFor("a", 2).isEmpty());
    }

    @Test
    void resetForgetsTheCount() {
        limiter.record("a", MINUTE);
        limiter.reset("a");

        assertTrue(limiter.blockedFor("a", 1).isEmpty());
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void doesNothingWhenSwitchedOff() {
        limiter.enabled = false;

        assertTrue(limiter.acquire("a", 0, MINUTE).isEmpty());
        limiter.record("a", MINUTE);
        assertTrue(limiter.blockedFor("a", 1).isEmpty());
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void dropsEndedWindowsBeforeTrackingMoreKeysThanTheMaximum() {
        for (int i = 0; i < RateLimiter.MAX_KEYS - 1; i++) {
            limiter.record("old" + i, MINUTE);
        }
        clock.advance(Duration.ofSeconds(30));
        limiter.record("recent", MINUTE);
        assertEquals(RateLimiter.MAX_KEYS, limiter.trackedKeys());
        clock.advance(Duration.ofSeconds(30));

        limiter.record("new", MINUTE);

        assertEquals(2, limiter.trackedKeys());
        assertTrue(limiter.blockedFor("recent", 1).isPresent());
    }

    @Test
    void resetsEverythingWhenTheMaximumIsFullOfRunningWindows() {
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            limiter.record("key" + i, MINUTE);
        }

        limiter.record("new", MINUTE);

        assertEquals(1, limiter.trackedKeys());
    }
}

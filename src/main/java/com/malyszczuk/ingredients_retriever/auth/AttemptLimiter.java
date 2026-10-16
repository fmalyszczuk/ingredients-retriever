package com.malyszczuk.ingredients_retriever.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Counts attempts per key (an address, an account, ...). Once a key has used up {@code maxAttempts} within the
 * window, it is blocked until the window that started with its first attempt has passed. Memory is bounded: when
 * there are more keys than {@code maxKeys}, the least recently used ones are forgotten, so flooding it with made-up
 * keys cannot exhaust memory. Everything is in memory, so a restart of the backend clears all counters.
 */
class AttemptLimiter {

    private static final class Counter {
        int count;
        Instant windowStart;
        Instant blockedUntil;
    }

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Counter> entries;

    AttemptLimiter(int maxAttempts, Duration window, int maxKeys, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Counter> eldest) {
                return size() > maxKeys;
            }
        };
    }

    /** How much longer the key stays blocked, or empty if it may go ahead. */
    synchronized Optional<Duration> blockedFor(String key) {
        Counter entry = entries.get(key);
        if (entry == null || entry.blockedUntil == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (now.isBefore(entry.blockedUntil)) {
            return Optional.of(Duration.between(now, entry.blockedUntil));
        }
        entries.remove(key);
        return Optional.empty();
    }

    /** Counts one attempt; the key is blocked from now on if that was its last allowed one. */
    synchronized void record(String key) {
        Instant now = clock.instant();
        Counter entry = entries.get(key);
        if (entry != null && !now.isBefore(expiry(entry))) {
            entry = null;
        }
        if (entry == null) {
            entry = new Counter();
            entry.windowStart = now;
            entries.put(key, entry);
        }
        entry.count++;
        if (entry.count >= maxAttempts && entry.blockedUntil == null) {
            entry.blockedUntil = now.plus(window);
        }
    }

    synchronized void clear(String key) {
        entries.remove(key);
    }

    // A block ends when its time is up; counting without a block ends with the window that it started in.
    private Instant expiry(Counter entry) {
        return entry.blockedUntil != null ? entry.blockedUntil : entry.windowStart.plus(window);
    }
}

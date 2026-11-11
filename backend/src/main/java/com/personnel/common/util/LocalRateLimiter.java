package com.personnel.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, atomic fixed windows measured with a monotonic clock. No active bucket eviction. */
public final class LocalRateLimiter {

    private static final long SECOND = 1_000_000_000L;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final int capacity;
    private final LongSupplier ticker;
    private long lastSweep;

    private static final class Bucket {

        final long start, duration;
        int used;

        Bucket(long start, long duration) {
            this.start = start;
            this.duration = duration;
        }

        boolean expired(long now) {
            return now - start >= duration;
        }
    }

    public LocalRateLimiter(int capacity) {
        this(capacity, System::nanoTime);
    }

    public LocalRateLimiter(int capacity, LongSupplier ticker) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.ticker = ticker;
        this.lastSweep = ticker.getAsLong();
    }

    /** Zero means admitted; positive values are a Retry-After duration in seconds. */
    public synchronized long acquire(String key, int limit, int windowSeconds) {
        if (
            limit < 1 || windowSeconds < 1 || windowSeconds > 86400
        ) throw new IllegalArgumentException("Invalid rate limit");
        long now = ticker.getAsLong();
        if (now - lastSweep >= SECOND) {
            buckets.values().removeIf(bucket -> bucket.expired(now));
            lastSweep = now;
        }
        Bucket bucket = buckets.get(key);
        if (bucket != null && bucket.expired(now)) {
            buckets.remove(key);
            bucket = null;
        }
        if (bucket == null) {
            if (buckets.size() >= capacity) return 1;
            bucket = new Bucket(now, windowSeconds * SECOND);
            buckets.put(key, bucket);
        }
        if (bucket.used >= limit) return Math.max(
            1,
            (bucket.duration - (now - bucket.start) + SECOND - 1) / SECOND
        );
        bucket.used++;
        return 0;
    }
}

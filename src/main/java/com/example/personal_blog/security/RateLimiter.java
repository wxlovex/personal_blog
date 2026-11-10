package com.example.personal_blog.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Bounded, atomic, single-instance limits. Active buckets are never evicted to admit new keys. */
@Component
@Slf4j
public class RateLimiter {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    private final int maxKeys;
    private long nextCleanup;

    @Autowired
    public RateLimiter(SecurityProperties properties) {
        this(Clock.systemUTC(), properties.getLimiterMaxKeys());
    }

    public RateLimiter(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    public synchronized void acquire(String key, int limit, Duration window) {
        long now = clock.millis();
        if (now >= nextCleanup) {
            buckets.values().removeIf(bucket -> bucket.expiresAt <= now);
            nextCleanup = now + 60000;
        }
        Bucket bucket = buckets.get(key);
        if (bucket == null || bucket.expiresAt <= now) {
            if (buckets.size() >= maxKeys) throw new RateLimitException(60);
            bucket = new Bucket(now + window.toMillis());
            buckets.put(key, bucket);
            nextCleanup = Math.min(nextCleanup, bucket.expiresAt);
        }
        if (bucket.used >= limit) {
            if (!bucket.reported) {
                // No credentials, tokens or submitted usernames are logged.
                log.warn("Security rate limit reached: policy={}", key.substring(0, key.indexOf(':')));
                bucket.reported = true;
            }
            throw new RateLimitException(Math.max(1, (bucket.expiresAt - now + 999) / 1000));
        }
        bucket.used++;
    }

    public synchronized void forget(String key) { buckets.remove(key); }

    private static class Bucket {
        final long expiresAt;
        int used;
        boolean reported;
        Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }
}

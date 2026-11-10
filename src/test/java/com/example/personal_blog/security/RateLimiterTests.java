package com.example.personal_blog.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimiterTests {
    @Test
    void concurrentRequestsCannotOverrunTheBudget() throws Exception {
        RateLimiter limiter = new RateLimiter(Clock.systemUTC(), 100);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger accepted = new AtomicInteger();
        try {
            java.util.List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) tasks.add(() -> {
                try { limiter.acquire("write:test", 5, Duration.ofMinutes(1)); accepted.incrementAndGet(); }
                catch (RateLimitException ignored) {}
                return null;
            });
            for (Future<Void> result : pool.invokeAll(tasks)) result.get();
            assertThat(accepted.get()).isEqualTo(5);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void expiredBucketsRecoverAndCapacityCannotEvictActiveBudgets() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        RateLimiter limiter = new RateLimiter(clock, 1);
        limiter.acquire("login:a", 1, Duration.ofSeconds(10));
        assertThatThrownBy(() -> limiter.acquire("login:b", 1, Duration.ofSeconds(10))).isInstanceOf(RateLimitException.class);
        assertThatThrownBy(() -> limiter.acquire("login:a", 1, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(10));
        when(clock.millis()).thenReturn(11000L);
        limiter.acquire("login:b", 1, Duration.ofSeconds(10));
    }
}

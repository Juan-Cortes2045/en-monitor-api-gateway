package com.energymonitor.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(2, Duration.ofMinutes(1), nanos::get);

    @Test
    void allowsTheLimitAndRefusesTheNextUntilTheWindowEnds() {
        assertThat(limiter.acquire("a")).isEmpty();
        nanos.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.acquire("a")).isEmpty();

        assertThat(limiter.acquire("a")).contains(Duration.ofSeconds(40));
    }

    @Test
    void opensANewWindowOnceTheOldOneEnds() {
        limiter.acquire("a");
        limiter.acquire("a");
        assertThat(limiter.acquire("a")).isPresent();

        nanos.addAndGet(Duration.ofMinutes(1).toNanos());

        assertThat(limiter.acquire("a")).isEmpty();
    }

    @Test
    void countsEachKeyApart() {
        limiter.acquire("a");
        limiter.acquire("a");

        assertThat(limiter.acquire("b")).isEmpty();
    }
}

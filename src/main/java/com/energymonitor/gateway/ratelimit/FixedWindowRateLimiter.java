package com.energymonitor.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts requests per key in fixed windows, in memory.
 *
 * <p>A key's window opens with its first request and lasts {@code window}; the entry then expires
 * and the next request opens a new one. The number of keys is bounded, so a flood of addresses
 * evicts old counters instead of growing the heap. Being in memory, the count is per instance of
 * the gateway and starts again on restart, which is enough for a single instance.
 */
public class FixedWindowRateLimiter {

    private static final long MAX_KEYS = 100_000;

    private final int requests;
    private final Duration window;
    private final Ticker ticker;
    private final Cache<String, Window> windows;

    /**
     * @param requests requests allowed per key within a window
     * @param window   length of a window
     * @param ticker   time source, {@code Ticker.systemTicker()} outside tests
     */
    public FixedWindowRateLimiter(int requests, Duration window, Ticker ticker) {
        this.requests = requests;
        this.window = window;
        this.ticker = ticker;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(MAX_KEYS)
                .ticker(ticker)
                .build();
    }

    /**
     * Counts one request.
     *
     * @param key who is asking, for example address and path
     * @return empty when the request is allowed; otherwise how long until the window ends
     */
    public Optional<Duration> acquire(String key) {
        Window current = windows.get(key, ignored -> new Window(ticker.read()));
        if (current.count.incrementAndGet() <= requests) {
            return Optional.empty();
        }
        Duration elapsed = Duration.ofNanos(ticker.read() - current.openedAt);
        Duration left = window.minus(elapsed);
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    private static final class Window {

        private final long openedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long openedAt) {
            this.openedAt = openedAt;
        }
    }
}

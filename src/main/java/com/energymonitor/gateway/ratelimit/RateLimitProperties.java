package com.energymonitor.gateway.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The limit on the public account endpoints, counted per client address and per path.
 *
 * @param requests how many {@code POST}s one address may send to one path within a window
 * @param window   the length of a window; the count starts again when it ends
 * @param paths    the limited paths
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        @DefaultValue("20") int requests,
        @DefaultValue("1m") Duration window,
        @DefaultValue({
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/google",
                "/api/v1/auth/refresh",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/email/verify",
                "/api/v1/auth/email/verification/resend"
        }) List<String> paths) {

    public RateLimitProperties {
        if (requests < 1) {
            throw new IllegalStateException("gateway.rate-limit.requests must be at least 1, was "
                    + requests + ".");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("gateway.rate-limit.window must be positive, was "
                    + window + ".");
        }
    }
}

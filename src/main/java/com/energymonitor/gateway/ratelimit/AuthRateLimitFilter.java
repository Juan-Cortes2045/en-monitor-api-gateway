package com.energymonitor.gateway.ratelimit;

import com.energymonitor.gateway.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers 429 to a client address that sends too many {@code POST}s to one public account
 * endpoint (sign in, registration, recovery, verification), before the request is forwarded.
 *
 * <p>The address is {@link HttpServletRequest#getRemoteAddr()}, which the container rewrites from
 * {@code X-Forwarded-For} only when the request comes from a trusted proxy
 * ({@code server.forward-headers-strategy=native}), so a client cannot pick its own key. It runs
 * after the CORS filter, so a browser can read the 429 too.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;
    private final List<PathPattern> paths;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter, List<String> paths,
                               ObjectMapper objectMapper, Clock clock) {
        this.limiter = limiter;
        this.paths = paths.stream().map(PathPatternParser.defaultInstance::parse).toList();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return paths.stream().noneMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<Duration> refused = limiter.acquire(request.getRemoteAddr() + " " + request.getRequestURI());
        if (refused.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        long seconds = Math.max(1, (refused.get().toMillis() + 999) / 1000);
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(Instant.now(clock), status.value(), status.getReasonPhrase(),
                        "Too many attempts. Try again in " + seconds + " seconds.",
                        request.getRequestURI()));
    }
}

package com.energymonitor.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Answers in the shared {@link ApiError} shape what the gateway itself cannot serve: a path no
 * route covers (404), and a service that refused the connection (503) or took longer than the
 * read timeout (504). The reason stays in the log; the body names neither the service nor its
 * address.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final Clock clock;

    public GatewayExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noRoute(HttpServletRequest request) {
        return answer(HttpStatus.NOT_FOUND, "No route serves this path.", request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiError> serviceDidNotAnswer(ResourceAccessException exception,
                                                        HttpServletRequest request) {
        boolean timedOut = causedBy(exception, SocketTimeoutException.class)
                || causedBy(exception, HttpTimeoutException.class);
        log.warn("{} {} not answered: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return timedOut
                ? answer(HttpStatus.GATEWAY_TIMEOUT, "The service took too long to answer.", request)
                : answer(HttpStatus.SERVICE_UNAVAILABLE, "The service is not available. Try again later.",
                        request);
    }

    private ResponseEntity<ApiError> answer(HttpStatus status, String message,
                                            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(Instant.now(clock), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }

    private static boolean causedBy(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}

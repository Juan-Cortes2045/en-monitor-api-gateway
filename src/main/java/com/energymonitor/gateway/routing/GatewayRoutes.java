package com.energymonitor.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The routes of the API, checked in order:
 *
 * <ol>
 *   <li>{@code /api/v1/auth/**}, {@code /api/v1/admin/users/**} and {@code /api/v1/admin/roles/**}
 *       go to the security service;</li>
 *   <li>every other {@code /api/v1/**} goes to energy-monitor-backend.</li>
 * </ol>
 *
 * <p>Nothing else is forwarded: {@code /internal/**} (the calls between the services) is refused
 * by the filter chain and any other path is answered 404 here. The path is passed unchanged.
 *
 * <p>The {@code Origin} header is dropped on the way: the gateway already answered CORS, and a
 * service that saw it would add its own CORS headers to the response.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRoutesProperties.class)
public class GatewayRoutes {

    /** The paths the security service serves. {@code /x/**} matches {@code /x} itself too. */
    static final String[] SECURITY_SERVICE_PATHS = {
            "/api/v1/auth/**",
            "/api/v1/admin/users/**",
            "/api/v1/admin/roles/**"
    };

    @Bean
    public RouterFunction<ServerResponse> apiRoutes(GatewayRoutesProperties properties) {
        RouterFunction<ServerResponse> securityService = route("security-service")
                .route(anyOf(SECURITY_SERVICE_PATHS), http())
                .before(uri(properties.securityServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> backend = route("backend")
                .route(path("/api/v1/**"), http())
                .before(uri(properties.backendUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        return securityService.and(backend);
    }

    private static RequestPredicate anyOf(String... patterns) {
        RequestPredicate predicate = path(patterns[0]);
        for (int i = 1; i < patterns.length; i++) {
            predicate = predicate.or(path(patterns[i]));
        }
        return predicate;
    }
}

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
 *   <li>{@code /api/v1/auth/**}, {@code /api/v1/admin/users/**}, {@code /api/v1/admin/roles/**}
 *       and {@code /api/v1/support/**} go to the security service;</li>
 *   <li>{@code /api/v1/homes/{homeId}/devices/**} and {@code /api/v1/appliance-types/**} go to the
 *       device service;</li>
 *   <li>{@code /api/v1/measurements/**}, {@code /api/v1/consumption-levels/**} and
 *       {@code /api/v1/homes/{homeId}/consumption/**} go to the measurement service;</li>
 *   <li>the rest of {@code /api/v1/homes/**}, and {@code /api/v1/home-types/**}, go to the home
 *       service: after the two above, so the devices and the consumption of a home do not end
 *       up there;</li>
 *   <li>{@code /api/v1/notifications/**} and {@code /api/v1/admin/notifications/**} go to the
 *       notification service;</li>
 *   <li>{@code /api/v1/alerts/**} goes to the alert service;</li>
 *   <li>{@code /api/v1/recommendations/**} goes to the recommendation service;</li>
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
            "/api/v1/admin/roles/**",
            "/api/v1/support/**"
    };

    /** The paths the device service serves. */
    static final String[] DEVICE_SERVICE_PATHS = {
            "/api/v1/homes/*/devices/**",
            "/api/v1/appliance-types/**"
    };

    /** The paths the measurement service serves. */
    static final String[] MEASUREMENT_SERVICE_PATHS = {
            "/api/v1/measurements/**",
            "/api/v1/consumption-levels/**",
            "/api/v1/homes/*/consumption/**"
    };

    /** The paths the home service serves, once the device and measurement ones are discounted. */
    static final String[] HOME_SERVICE_PATHS = {
            "/api/v1/homes/**",
            "/api/v1/home-types/**"
    };

    /** The paths the notification service serves: a user's channels and the administrator notifications. */
    static final String[] NOTIFICATION_SERVICE_PATHS = {
            "/api/v1/notifications/**",
            "/api/v1/admin/notifications/**"
    };

    /** The paths the alert service serves: the alerts of a home. */
    static final String[] ALERT_SERVICE_PATHS = {
            "/api/v1/alerts/**"
    };

    /** The paths the recommendation service serves: the recommendations of a home. */
    static final String[] RECOMMENDATION_SERVICE_PATHS = {
            "/api/v1/recommendations/**"
    };

    @Bean
    public RouterFunction<ServerResponse> apiRoutes(GatewayRoutesProperties properties) {
        RouterFunction<ServerResponse> securityService = route("security-service")
                .route(anyOf(SECURITY_SERVICE_PATHS), http())
                .before(uri(properties.securityServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> deviceService = route("device-service")
                .route(anyOf(DEVICE_SERVICE_PATHS), http())
                .before(uri(properties.deviceServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> measurementService = route("measurement-service")
                .route(anyOf(MEASUREMENT_SERVICE_PATHS), http())
                .before(uri(properties.measurementServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> homeService = route("home-service")
                .route(anyOf(HOME_SERVICE_PATHS), http())
                .before(uri(properties.homeServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> notificationService = route("notification-service")
                .route(anyOf(NOTIFICATION_SERVICE_PATHS), http())
                .before(uri(properties.notificationServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> alertService = route("alert-service")
                .route(anyOf(ALERT_SERVICE_PATHS), http())
                .before(uri(properties.alertServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> recommendationService = route("recommendation-service")
                .route(anyOf(RECOMMENDATION_SERVICE_PATHS), http())
                .before(uri(properties.recommendationServiceUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        RouterFunction<ServerResponse> backend = route("backend")
                .route(path("/api/v1/**"), http())
                .before(uri(properties.backendUrl()))
                .before(removeRequestHeader(HttpHeaders.ORIGIN))
                .build();
        // The order is the precedence: /homes/*/devices and /homes/*/consumption before /homes/**.
        return securityService.and(deviceService).and(measurementService).and(homeService)
                .and(notificationService).and(alertService).and(recommendationService).and(backend);
    }

    private static RequestPredicate anyOf(String... patterns) {
        RequestPredicate predicate = path(patterns[0]);
        for (int i = 1; i < patterns.length; i++) {
            predicate = predicate.or(path(patterns[i]));
        }
        return predicate;
    }
}

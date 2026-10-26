package com.energymonitor.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the services behind the gateway listen.
 *
 * @param securityServiceUrl en-monitor-security-service: accounts, sessions and RBAC administration
 * @param deviceServiceUrl   en-monitor-device-service: the devices of a home and the appliance types
 * @param measurementServiceUrl en-monitor-measurement-service: readings, consumption of a home and
 *                              consumption levels
 * @param homeServiceUrl     en-monitor-home-service: homes, their members and thresholds, and
 *                           the home types
 * @param backendUrl         energy-monitor-backend: everything else under {@code /api/v1}
 */
@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRoutesProperties(
        @DefaultValue("http://localhost:8082") URI securityServiceUrl,
        @DefaultValue("http://localhost:8084") URI deviceServiceUrl,
        @DefaultValue("http://localhost:8085") URI measurementServiceUrl,
        @DefaultValue("http://localhost:8086") URI homeServiceUrl,
        @DefaultValue("http://localhost:8083") URI backendUrl) {
}

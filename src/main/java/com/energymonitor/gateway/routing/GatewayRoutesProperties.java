package com.energymonitor.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the services behind the gateway listen.
 *
 * @param securityServiceUrl en-monitor-security-service: accounts, sessions and RBAC administration
 * @param backendUrl         energy-monitor-backend: everything else under {@code /api/v1}
 */
@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRoutesProperties(
        @DefaultValue("http://localhost:8082") URI securityServiceUrl,
        @DefaultValue("http://localhost:8083") URI backendUrl) {
}

package com.energymonitor.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The gateway in front of five fake services. Shared by the tests that need a running context, so
 * they all reuse one.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class GatewayTest {

    protected static final String WEB_ORIGIN = "http://localhost:5173";

    protected static final FakeService SECURITY_SERVICE = new FakeService("security");
    protected static final FakeService DEVICE_SERVICE = new FakeService("device");
    protected static final FakeService MEASUREMENT_SERVICE = new FakeService("measurement");
    protected static final FakeService HOME_SERVICE = new FakeService("home");
    protected static final FakeService BACKEND = new FakeService("backend");

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.security-service-url", SECURITY_SERVICE::url);
        registry.add("gateway.routes.device-service-url", DEVICE_SERVICE::url);
        registry.add("gateway.routes.measurement-service-url", MEASUREMENT_SERVICE::url);
        registry.add("gateway.routes.home-service-url", HOME_SERVICE::url);
        registry.add("gateway.routes.backend-url", BACKEND::url);
        registry.add("security.jwt.public-key-path", TestTokens::publicKeyFile);
        registry.add("security.cors.allowed-origins", () -> WEB_ORIGIN);
    }

    @BeforeEach
    void forgetRequests() {
        SECURITY_SERVICE.reset();
        DEVICE_SERVICE.reset();
        MEASUREMENT_SERVICE.reset();
        HOME_SERVICE.reset();
        BACKEND.reset();
    }

    protected static String bearer(String subject) {
        return "Bearer " + TestTokens.valid(subject);
    }
}

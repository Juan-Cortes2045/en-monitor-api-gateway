package com.energymonitor.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The gateway in front of two fake services. Shared by the tests that need a running context, so
 * they all reuse one.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class GatewayTest {

    protected static final String WEB_ORIGIN = "http://localhost:5173";

    protected static final FakeService SECURITY_SERVICE = new FakeService("security");
    protected static final FakeService BACKEND = new FakeService("backend");

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.security-service-url", SECURITY_SERVICE::url);
        registry.add("gateway.routes.backend-url", BACKEND::url);
        registry.add("security.jwt.public-key-path", TestTokens::publicKeyFile);
        registry.add("security.cors.allowed-origins", () -> WEB_ORIGIN);
    }

    @BeforeEach
    void forgetRequests() {
        SECURITY_SERVICE.reset();
        BACKEND.reset();
    }

    protected static String bearer(String subject) {
        return "Bearer " + TestTokens.valid(subject);
    }
}

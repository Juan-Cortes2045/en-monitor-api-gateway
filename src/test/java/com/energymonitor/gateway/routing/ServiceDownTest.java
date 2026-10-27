package com.energymonitor.gateway.routing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.gateway.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A service that does not accept the connection: nothing listens on port 9 of the loopback. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceDownTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.security-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.device-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.measurement-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.home-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.notification-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.alert-service-url", () -> "http://127.0.0.1:9");
        registry.add("gateway.routes.recommendation-service-url", () -> "http://127.0.0.1:9");
        registry.add("security.jwt.public-key-path", TestTokens::publicKeyFile);
    }

    @Test
    void answersServiceUnavailable() throws Exception {
        mvc.perform(get("/api/v1/homes")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/v1/homes"))
                .andExpect(jsonPath("$.message").value("The service is not available. Try again later."));
    }

    @Test
    void answersServiceUnavailableWhenTheHomeServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/homes/H1/members")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/homes/H1/members"));
    }

    @Test
    void answersServiceUnavailableWhenTheNotificationServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/notifications/preferences"));
    }

    @Test
    void answersServiceUnavailableWhenTheRecommendationServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/recommendations").param("homeId", "H1")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/recommendations"));
    }

    @Test
    void answersServiceUnavailableWhenTheAlertServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("homeId", "H1")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/alerts"));
    }

    @Test
    void answersServiceUnavailableWhenTheDeviceServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/homes/H1/devices")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/homes/H1/devices"));
    }

    @Test
    void answersServiceUnavailableWhenTheMeasurementServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/homes/H1/consumption/summary")
                        .header("Authorization", "Bearer " + TestTokens.valid("use0000001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path").value("/api/v1/homes/H1/consumption/summary"));
    }
}

package com.energymonitor.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.gateway.FakeService;
import com.energymonitor.gateway.GatewayTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Which service each path reaches, and what it receives. */
class GatewayRoutesTest extends GatewayTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sendsTheAccountRoutesToTheSecurityService() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("security"));

        FakeService.Received received = SECURITY_SERVICE.last();
        assertThat(received.method()).isEqualTo("POST");
        assertThat(received.path()).isEqualTo("/api/v1/auth/login");
        assertThat(received.body()).isEqualTo("{\"email\":\"ana@example.com\",\"password\":\"x\"}");
        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void sendsTheRbacAdministrationToTheSecurityService() throws Exception {
        mvc.perform(get("/api/v1/admin/users").queryParam("page", "1")
                        .header("Authorization", bearer("adm0000001")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer("adm0000001")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/users/use0000001/roles/ADMIN")
                        .header("Authorization", bearer("adm0000001")))
                .andExpect(status().isOk());

        assertThat(SECURITY_SERVICE.received()).extracting(FakeService.Received::path)
                .containsExactly("/api/v1/admin/users?page=1", "/api/v1/admin/roles",
                        "/api/v1/admin/users/use0000001/roles/ADMIN");
        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void sendsTheDevicesOfAHomeToTheDeviceService() throws Exception {
        String authorization = bearer("use0000001");

        mvc.perform(get("/api/v1/homes/H1/devices").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("device"));
        mvc.perform(post("/api/v1/homes/H1/devices").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceCode\":\"ABC123\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/homes/H1/devices/D1").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nevera\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/homes/H1/devices/D1/credentials").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/homes/H1/devices/D1").header("Authorization", authorization))
                .andExpect(status().isOk());

        assertThat(DEVICE_SERVICE.received()).extracting(FakeService.Received::method,
                        FakeService.Received::path)
                .containsExactly(tuple("GET", "/api/v1/homes/H1/devices"),
                        tuple("POST", "/api/v1/homes/H1/devices"),
                        tuple("PUT", "/api/v1/homes/H1/devices/D1"),
                        tuple("POST", "/api/v1/homes/H1/devices/D1/credentials"),
                        tuple("DELETE", "/api/v1/homes/H1/devices/D1"));
        assertThat(DEVICE_SERVICE.received().get(1).body()).isEqualTo("{\"deviceCode\":\"ABC123\"}");
        assertThat(DEVICE_SERVICE.last().header("Authorization")).isEqualTo(authorization);
        assertThat(DEVICE_SERVICE.last().header("Origin")).isNull();
        assertThat(BACKEND.received()).isEmpty();
        assertThat(SECURITY_SERVICE.received()).isEmpty();
    }

    @Test
    void sendsTheApplianceCatalogueToTheDeviceService() throws Exception {
        mvc.perform(get("/api/v1/appliance-types").header("Authorization", bearer("use0000001"))
                        .header("Origin", WEB_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("device"));

        assertThat(DEVICE_SERVICE.last().path()).isEqualTo("/api/v1/appliance-types");
        assertThat(DEVICE_SERVICE.last().header("Origin")).isNull();
        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void keepsTheRestOfAHomeInTheBackend() throws Exception {
        String authorization = bearer("use0000001");

        mvc.perform(get("/api/v1/homes/H1").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/homes/H1/members").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/homes/H1/devices-report").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/notifications/push/devices").header("Authorization", authorization))
                .andExpect(status().isOk());

        assertThat(BACKEND.received()).extracting(FakeService.Received::path)
                .containsExactly("/api/v1/homes/H1", "/api/v1/homes/H1/members",
                        "/api/v1/homes/H1/devices-report", "/api/v1/notifications/push/devices");
        assertThat(DEVICE_SERVICE.received()).isEmpty();
    }

    @Test
    void sendsEverythingElseToTheBackendWithTheToken() throws Exception {
        String authorization = bearer("use0000001");

        mvc.perform(get("/api/v1/homes").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("backend"));
        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/homes/H1/members/me").header("Authorization", authorization))
                .andExpect(status().isOk());

        assertThat(BACKEND.received()).extracting(FakeService.Received::path)
                .containsExactly("/api/v1/homes", "/api/v1/admin/notifications",
                        "/api/v1/homes/H1/members/me");
        assertThat(BACKEND.last().header("Authorization")).isEqualTo(authorization);
        assertThat(SECURITY_SERVICE.received()).isEmpty();
        assertThat(DEVICE_SERVICE.received()).isEmpty();
    }

    @Test
    void tellsTheServiceWhoCalled() throws Exception {
        mvc.perform(get("/api/v1/homes").header("Authorization", bearer("use0000001"))
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        }))
                .andExpect(status().isOk());

        assertThat(BACKEND.last().header("X-Forwarded-For")).isEqualTo("203.0.113.7");
    }

    @Test
    void tellsTheDeviceServiceWhoCalled() throws Exception {
        mvc.perform(get("/api/v1/homes/H1/devices").header("Authorization", bearer("use0000001"))
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.8");
                            return request;
                        }))
                .andExpect(status().isOk());

        assertThat(DEVICE_SERVICE.last().header("X-Forwarded-For")).isEqualTo("203.0.113.8");
    }

    @Test
    void answersNotFoundForAPathNoRouteServes() throws Exception {
        mvc.perform(get("/somewhere/else").header("Authorization", bearer("use0000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/somewhere/else"));

        assertThat(BACKEND.received()).isEmpty();
        assertThat(SECURITY_SERVICE.received()).isEmpty();
        assertThat(DEVICE_SERVICE.received()).isEmpty();
    }

    @Test
    void answersGatewayTimeoutWhenTheServiceIsTooSlow() throws Exception {
        mvc.perform(get("/api/v1/slow-report").header("Authorization", bearer("use0000001")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.path").value("/api/v1/slow-report"));
    }
}

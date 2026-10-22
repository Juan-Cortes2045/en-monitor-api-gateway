package com.energymonitor.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
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
    void answersNotFoundForAPathNoRouteServes() throws Exception {
        mvc.perform(get("/somewhere/else").header("Authorization", bearer("use0000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/somewhere/else"));

        assertThat(BACKEND.received()).isEmpty();
        assertThat(SECURITY_SERVICE.received()).isEmpty();
    }

    @Test
    void answersGatewayTimeoutWhenTheServiceIsTooSlow() throws Exception {
        mvc.perform(get("/api/v1/slow-report").header("Authorization", bearer("use0000001")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.path").value("/api/v1/slow-report"));
    }
}

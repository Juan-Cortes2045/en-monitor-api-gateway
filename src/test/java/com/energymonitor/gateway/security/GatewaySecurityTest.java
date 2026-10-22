package com.energymonitor.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.gateway.GatewayTest;
import com.energymonitor.gateway.TestTokens;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** What the gateway refuses before forwarding, and how it answers CORS. */
class GatewaySecurityTest extends GatewayTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void refusesAProtectedRouteWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/homes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/homes"));

        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        refuses("Bearer " + TestTokens.expired("use0000001"));
    }

    @Test
    void refusesATokenOfAnotherIssuer() throws Exception {
        refuses("Bearer " + TestTokens.fromAnotherIssuer("use0000001"));
    }

    @Test
    void refusesATokenSignedWithAnotherKey() throws Exception {
        refuses("Bearer " + TestTokens.signedWithAnotherKey("use0000001"));
    }

    @Test
    void refusesAGarbledToken() throws Exception {
        refuses("Bearer not-a-token");
    }

    @Test
    void protectsTheAccountRoutesThatNeedASession() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/account")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());

        assertThat(SECURITY_SERVICE.received()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/google", "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
            "/api/v1/auth/email/verify", "/api/v1/auth/email/verification/resend"})
    void letsThePublicAccountRoutesThroughWithoutAToken(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        assertThat(SECURITY_SERVICE.last().path()).isEqualTo(path);
    }

    @Test
    void neverForwardsTheInternalApi() throws Exception {
        mvc.perform(get("/internal/administrators").header("X-Internal-Token", "guess"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/use0000001/permissions")
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(SECURITY_SERVICE.received()).isEmpty();
        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void answersThePreflightOfTheWebOriginItself() throws Exception {
        mvc.perform(options("/api/v1/homes")
                        .header("Origin", WEB_ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("DELETE")));

        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void refusesThePreflightOfAnUnknownOrigin() throws Exception {
        mvc.perform(options("/api/v1/homes")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());

        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void answersCorsOnceAndHidesTheOriginFromTheService() throws Exception {
        mvc.perform(get("/api/v1/homes").header("Origin", WEB_ORIGIN)
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Access-Control-Allow-Origin", WEB_ORIGIN));

        assertThat(BACKEND.last().header("Origin")).isNull();
    }

    @Test
    void reportsItsHealthWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private void refuses(String authorization) throws Exception {
        mvc.perform(get("/api/v1/homes").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertThat(BACKEND.received()).isEmpty();
    }
}

package com.energymonitor.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.gateway.GatewayTest;
import com.energymonitor.gateway.TestTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The web app's access token arrives in an HttpOnly cookie and leaves as a bearer header. */
class AccessTokenCookieTest extends GatewayTest {

    @Autowired
    private MockMvc mvc;

    private static Cookie access(String token) {
        return new Cookie("em_access", token);
    }

    @Test
    void forwardsTheCookieAsABearerHeader() throws Exception {
        String token = TestTokens.valid("use0000001");

        mvc.perform(get("/api/v1/other").cookie(access(token))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());

        assertThat(BACKEND.last().header("Authorization")).isEqualTo("Bearer " + token);
    }

    @Test
    void ignoresTheCookieWithoutTheRequestedWithHeader() throws Exception {
        // A forged cross-site request (a form, an image) cannot add the header.
        mvc.perform(get("/api/v1/other").cookie(access(TestTokens.valid("use0000001"))))
                .andExpect(status().isUnauthorized());

        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void refusesAnExpiredCookie() throws Exception {
        mvc.perform(get("/api/v1/other").cookie(access(TestTokens.expired("use0000001")))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        assertThat(BACKEND.received()).isEmpty();
    }

    @Test
    void leavesThePublicAccountEndpointsAlone() throws Exception {
        // An expired access cookie must not break the refresh that renews it.
        mvc.perform(post("/api/v1/auth/refresh").cookie(access(TestTokens.expired("use0000001")))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());

        assertThat(SECURITY_SERVICE.last().header("Authorization")).isNull();
    }

    @Test
    void anExplicitHeaderWins() throws Exception {
        String header = bearer("use0000002");

        mvc.perform(get("/api/v1/other").cookie(access(TestTokens.valid("use0000001")))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Authorization", header))
                .andExpect(status().isOk());

        assertThat(BACKEND.last().header("Authorization")).isEqualTo(header);
    }
}

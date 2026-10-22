package com.energymonitor.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.gateway.GatewayTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The limit of the public account endpoints: 3 POSTs per address and path in the test profile. */
class AuthRateLimitTest extends GatewayTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void refusesTheFourthSignInFromTheSameAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(login("198.51.100.1")).andExpect(status().isOk());
        }

        mvc.perform(login("198.51.100.1").header("Origin", WEB_ORIGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Access-Control-Allow-Origin", WEB_ORIGIN))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

        assertThat(SECURITY_SERVICE.received()).hasSize(3);
    }

    @Test
    void countsEachAddressAndPathApart() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(login("198.51.100.2")).andExpect(status().isOk());
        }

        mvc.perform(login("198.51.100.3")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}")
                        .with(from("198.51.100.2")))
                .andExpect(status().isOk());
    }

    @Test
    void leavesTheOtherRoutesUnlimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/homes").header("Authorization", bearer("use0000001"))
                            .with(from("198.51.100.4")))
                    .andExpect(status().isOk());
        }
    }

    private static MockHttpServletRequestBuilder login(String address) {
        return post("/api/v1/auth/login").contentType("application/json").content("{}")
                .with(from(address));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}

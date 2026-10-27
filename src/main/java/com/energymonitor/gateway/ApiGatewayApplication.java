package com.energymonitor.gateway;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The single entry point of the EnergyMonitor API: it routes each request to the service that owns
 * it, answers CORS, validates the access token and rate-limits the public account endpoints before
 * anything reaches them.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /** The clock of the error timestamps, replaceable in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

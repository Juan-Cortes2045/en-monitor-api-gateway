package com.energymonitor.gateway.security;

import com.energymonitor.gateway.ratelimit.AuthRateLimitFilter;
import com.energymonitor.gateway.ratelimit.FixedWindowRateLimiter;
import com.energymonitor.gateway.ratelimit.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * What the gateway checks before forwarding anything.
 *
 * <ul>
 *   <li>{@code /internal/**} is refused whatever the caller sends: those are the calls between
 *       the services, and they never come through here.</li>
 *   <li>The public account endpoints of the security service (sign in, registration, Google,
 *       refresh, recovery, verification), the preflights and the gateway's health pass without a
 *       token.</li>
 *   <li>Everything else needs a valid access token: signed by the security service, with its
 *       issuer and not expired. A missing or invalid one is answered 401 here; a valid one is
 *       forwarded as is, and the service checks it again and decides the permissions.</li>
 * </ul>
 *
 * <p>Stateless, no session and no CSRF: the API authenticates with a bearer token, never with a
 * cookie, so there is no ambient credential a forged request could ride on.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityFilterChainConfiguration {

    /** The security service endpoints that work without a token, as it declares them. */
    static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verification/resend"
    };

    private static final String[] INFRASTRUCTURE_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationEntryPoint entryPoint,
                                                   JwtAccessDeniedHandler accessDeniedHandler,
                                                   RateLimitProperties rateLimit,
                                                   ObjectMapper objectMapper,
                                                   Clock clock) throws Exception {
        // Built here rather than declared as a bean, so the container does not also register it
        // as a plain servlet filter and count every request twice.
        AuthRateLimitFilter rateLimitFilter = new AuthRateLimitFilter(
                new FixedWindowRateLimiter(rateLimit.requests(), rateLimit.window(),
                        Ticker.systemTicker()),
                rateLimit.paths(), objectMapper, clock);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(INFRASTRUCTURE_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(rateLimitFilter, CorsFilter.class);
        return http.build();
    }
}

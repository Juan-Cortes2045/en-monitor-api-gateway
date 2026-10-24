package com.energymonitor.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The web app's access token, from its HttpOnly cookie to the {@code Authorization} header.
 *
 * <p>The security service gives the web app its tokens in cookies script cannot read
 * ({@code X-Session-Transport: cookie}). This filter runs before the bearer token
 * authentication: when a request brings no {@code Authorization} header but the
 * {@value #ACCESS_COOKIE} cookie, it adds {@code Authorization: Bearer <cookie>}. The gateway
 * then validates it as any other bearer token and forwards the header, so the services behind it
 * need no change. The mobile app keeps sending the header itself.
 *
 * <p>The cookie is only used when the request also carries {@code X-Requested-With:
 * XMLHttpRequest}. The cookie is {@code SameSite=Strict} already; the header is the second
 * barrier against a forged cross-site request, because a form or an image cannot send it and a
 * cross-origin script cannot either unless the CORS policy allows it.
 *
 * <p>Not applied to the public account endpoints: an expired access cookie on the refresh
 * request would otherwise fail authentication on the very call that renews it.
 */
public class AccessTokenCookieFilter extends OncePerRequestFilter {

    static final String ACCESS_COOKIE = "em_access";
    static final String REQUESTED_WITH = "X-Requested-With";
    static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    private final RequestMatcher publicEndpoints;

    /** @param publicEndpoints the routes that work without a token */
    public AccessTokenCookieFilter(RequestMatcher publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<String> token = request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && XML_HTTP_REQUEST.equals(request.getHeader(REQUESTED_WITH))
                && !publicEndpoints.matches(request)
                ? accessCookie(request) : Optional.empty();
        chain.doFilter(token.<HttpServletRequest>map(t -> new WithBearer(request, t)).orElse(request),
                response);
    }

    private static Optional<String> accessCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    /** The same request with an {@code Authorization} header, as the proxy forwards it. */
    private static final class WithBearer extends HttpServletRequestWrapper {

        private final String authorization;

        WithBearer(HttpServletRequest request, String token) {
            super(request);
            this.authorization = "Bearer " + token;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(authorization)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.add(HttpHeaders.AUTHORIZATION);
            return Collections.enumeration(names);
        }
    }
}

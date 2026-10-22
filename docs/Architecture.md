# Architecture

The gateway has no domain: it routes, validates and refuses. Its code is therefore organised by
concern rather than in hexagonal layers.

```
com.energymonitor.gateway
│
├── routing      → GatewayRoutes (which service owns each path), service addresses
├── security     → filter chain, JWT validation with the public key, CORS
├── ratelimit    → fixed-window limiter of the public account endpoints
└── error        → ApiError and the 404 / 503 / 504 answers
```

## A request

```
client ──► nginx (HTTPS) ──► gateway :8080
                               │ 1. CORS: preflight answered here; other origins refused
                               │ 2. rate limit: public account POSTs, per address and path
                               │ 3. token: required except on the public account endpoints
                               │ 4. route: Origin dropped, X-Forwarded-For appended
                               ├──► en-monitor-security-service :8082   /api/v1/auth/**, /api/v1/admin/users|roles/**
                               └──► energy-monitor-backend :8083        any other /api/v1/**
```

## Decisions

- **Spring Cloud Gateway on the servlet stack** (Server Web MVC), like the services behind it: the
  same Spring Security configuration, the same error shape and the same way of testing.
- **The token is validated twice.** The gateway rejects a missing, forged or expired token before
  it costs a service anything; each service still validates it, so a service reached without the
  gateway is not open. The gateway decides no permission: the services resolve them live.
- **The public routes are the security service's own list** (register, login, google, refresh,
  password forgot and reset, email verify and resend). A new public endpoint there must be added
  to `SecurityFilterChainConfiguration.PUBLIC_ENDPOINTS` here too.
- **CORS only here.** The gateway drops `Origin` before forwarding, so a service never answers
  CORS and the browser never sees the headers twice.
- **`/internal/**` is refused, not routed.** Those calls go between the services over the private
  network, with their own token.
- **The rate limit is in memory.** It is per instance and starts again on restart; enough for one
  instance. Several instances would need a shared store (Redis).
- **Apache HttpClient 5** forwards the requests: the JDK client fails on a `DELETE` without a body.

## Migration status

Phase 5 of the security service migration (see en-monitor-security-service `docs/Architecture.md`)
added the gateway; phase 6 runs it in the infra compose files behind the web container's nginx,
with its own CI/CD workflow.

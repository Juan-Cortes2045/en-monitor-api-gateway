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
                               ├──► en-monitor-device-service :8084     /api/v1/homes/*/devices/**, /api/v1/appliance-types/**
                               ├──► en-monitor-measurement-service :8085 /api/v1/measurements/**, /api/v1/consumption-levels/**,
                               │                                         /api/v1/homes/*/consumption/**
                               ├──► en-monitor-home-service :8086       the rest of /api/v1/homes/**, /api/v1/home-types/**
                               ├──► en-monitor-notification-service :8087 /api/v1/notifications/**, /api/v1/admin/notifications/**
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
- **Routes by path prefix, most specific first.** The device and measurement routes are matched
  before the home service's `/api/v1/homes/**`, and all of them before the backend's `/api/v1/**`,
  so moving a module out of the backend only adds a route here; the paths the clients call do not
  change.
- **Apache HttpClient 5** forwards the requests: the JDK client fails on a `DELETE` without a body.

## Migration status

Phase 5 of the security service migration (see en-monitor-security-service `docs/Architecture.md`)
added the gateway; phase 6 runs it in the infra compose files behind the web container's nginx,
with its own CI/CD workflow.

The device service migration (see en-monitor-device-service) added the route to the devices of a
home and the appliance catalogue; the clients call the same paths as before.

The measurement service migration (see en-monitor-measurement-service) added the route to the
readings, the consumption of a home and the consumption levels, with the same paths as before.

The home service migration (see en-monitor-home-service) added the route to the homes, their
members and thresholds, and the home types, with the same paths as before. The home service must
be running before a gateway with this route is deployed: until then those paths answer 503.

The notification service migration (see en-monitor-notification-service) added the route to a
user's notification channels and the administrator notifications, with the same paths as before.
`/api/v1/admin/notifications/**` is not one of the security service's admin paths, so it reaches
this route. The notification service must be running before a gateway with this route is
deployed: until then those paths answer 503.

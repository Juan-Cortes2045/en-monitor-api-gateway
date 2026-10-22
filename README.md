# api-gateway

The single entry point of the **EnergyMonitor** API. The web and mobile clients call only this
service, on the address they always used (`:8080`), and it forwards each request to the service
that owns it:

| Path | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/admin/users/**`, `/api/v1/admin/roles/**` | en-monitor-security-service (`:8082`) |
| any other `/api/v1/**` | energy-monitor-backend (`:8083`) |
| `/internal/**` | never forwarded (401 without a token, 403 with one) |
| anything else | 404 |

Before forwarding, it:

- **answers CORS** for the whole API, and drops the `Origin` header, so the services never add
  CORS headers of their own;
- **validates the access token** (RS256 signature of the security service, issuer, expiry) on every
  route except the public account endpoints. A missing or invalid token is answered 401 here; a
  valid one is forwarded as is, and the services check it again and decide the permissions;
- **limits the public account endpoints** (sign in, registration, Google, refresh, recovery,
  verification): 20 `POST`s per client address and path per minute, then 429 with `Retry-After`;
- **answers 503** when a service refuses the connection and **504** when it takes longer than 30 s.

Every answer it produces itself has the same JSON shape as the services' errors
(`timestamp`, `status`, `error`, `message`, `path`).

The design is in [`docs/Architecture.md`](docs/Architecture.md).

## Tech stack

Java 21, Spring Boot 4.0.8, Spring Cloud Gateway Server Web MVC 5.0 (Spring Cloud 2025.1.3),
Spring Security with the OAuth2 resource server, Apache HttpClient 5 and Caffeine.

Spring Boot 4.0 rather than the 4.1 of the other services: Spring Cloud 2025.1 is the release
train that supports it, and no generally available train supports 4.1 yet.

## Running locally

Requirements: Java 21, and the public key of the security service (`public.pem`, see "RSA key
pair" in its README).

1. `cp .env.example .env` and set `SECURITY_JWT_PUBLIC_KEY_PATH` (absolute path) and
   `SECURITY_CORS_ALLOWED_ORIGINS` (`http://localhost:5173` for the web app in development).
2. `./mvnw spring-boot:run`

It answers on `http://localhost:8080`. Health: `/actuator/health`. It starts without the services
behind it; their routes answer 503 until they are up.

The three services on one machine:

| Service | Port | Started with |
|---|---|---|
| api-gateway | 8080 | `./mvnw spring-boot:run` here |
| en-monitor-security-service | 8082 | `./mvnw spring-boot:run` in its repository |
| energy-monitor-backend | 8083 | `./mvnw spring-boot:run` in its repository |

The web app keeps `VITE_API_BASE_URL=http://localhost:8080`.

## Behind nginx

In production nginx terminates HTTPS and forwards to the gateway. The gateway takes the caller's
address from `X-Forwarded-For` only when the connection comes from loopback or a Docker network
(`172.16.0.0/12`; `server.forward-headers-strategy=native` and `server.tomcat.remoteip.internal-proxies`),
so a client cannot choose the address the rate limit counts. Not every private network is trusted:
a machine of the LAN could otherwise pass as `127.0.0.1`. The gateway appends that address to
`X-Forwarded-For` for the services, which trust it the same way.

## Docker and CI/CD

```bash
docker build -t energy-monitor-gateway .
```

The infra repository builds the image and runs it in front of the other two services
(`infra/docker-compose.yml`, `infra/docker-compose.prod.yml`). There it publishes no port: the
web container's nginx forwards `/api/` to `gateway:8080`, the services are reached as
`security:8082` and `backend:8083`, and it receives only `public.pem`. Its container health is
`/actuator/health`.

`.github/workflows/ci-cd.yml` runs `./mvnw test` on every pull request and push to `dev` and
`main`, and after a push to `main` (and the approval of the `production` environment) deploys it
with `infra/deploy/deploy.sh gateway`.

## Tests

```bash
./mvnw test
```

They need nothing running: two fake services on free local ports stand in for the real ones, and
the key pair is generated for the run.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `SECURITY_JWT_PUBLIC_KEY_PATH` | — (required) | public key of the security service |
| `SECURITY_CORS_ALLOWED_ORIGINS` | empty (none) | browser origins allowed, comma separated |
| `SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS` | empty | origin patterns, such as `https://*.example.com` |
| `SECURITY_SERVICE_URL` | `http://localhost:8082` | en-monitor-security-service |
| `BACKEND_URL` | `http://localhost:8083` | energy-monitor-backend |
| `GATEWAY_CONNECT_TIMEOUT` | `2s` | connection to a service |
| `GATEWAY_READ_TIMEOUT` | `30s` | answer of a service |
| `GATEWAY_RATE_LIMIT_REQUESTS` | `20` | `POST`s per address and path in a window |
| `GATEWAY_RATE_LIMIT_WINDOW` | `1m` | length of the window |

# Redis authentication rate limiting

The optional `rate-limit` Spring profile protects anonymous `POST /api/v1/auth/login`
and `POST /api/v1/auth/bootstrap` requests. It is intentionally opt-in so
API-only development and H2-backed tests do not require a running Redis instance.

## Run

```bash
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=rate-limit mvn spring-boot:run
```

Combine with ingestion when required: `SPRING_PROFILES_ACTIVE=rate-limit,ingestion`.

Configuration (environment variables):

| Variable | Default | Meaning |
| --- | --- | --- |
| `AUTH_RATE_LIMIT_MAX_REQUESTS` | `10` | Maximum requests per source address per window |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` | Fixed window length, 1–86400 seconds |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |

Both endpoints share the same budget for a given socket remote address. A
single atomic Redis Lua script increments the counter, attaches the TTL and
returns the remaining window. The limit is shared across API replicas.
Keys use a SHA-256 digest of the source address instead of its literal value.

Requests over budget return HTTP **429** with `Retry-After` (whole seconds),
a JSON error and `Cache-Control: no-store`. If Redis cannot be reached or
returns an invalid script result, these authentication endpoints **fail closed**
with HTTP **503**, `Retry-After: 1`; unrelated API routes are unaffected.

## Security and operations

The limiter uses the servlet socket remote address, **not** client-supplied
`X-Forwarded-For` headers. Behind a reverse proxy, that may mean all clients
share the proxy's budget. Configure trusted ingress/network forwarding so the
application receives a verified client address; never accept arbitrary client
forwarding headers. This is an IP-based baseline, not an account-level defense
against distributed credential stuffing. Layer with perimeter controls,
account-focused abuse detection, and monitoring for production deployments.

Run `mvn clean verify` to exercise the rate-limit filter and Redis script
contract tests. Existing tests run without the opt-in profile.

# JWT filter failure boundaries

The JWT request filter distinguishes authentication failures from application
and infrastructure failures:

- A malformed Bearer header, duplicate Authorization headers, invalid
  signature, expired token, or revoked account produces HTTP 401.
- A valid authenticated request proceeds to normal Spring Security RBAC
  checks (HTTP 403 for insufficient roles).
- Database outages and downstream exceptions are not caught and disguised
  as authentication errors. They propagate to normal error handling and
  operational monitoring.

Bearer scheme matching is case-insensitive per HTTP authentication semantics.
A request without an Authorization header proceeds to the security chain,
which enforces the endpoint's public or authenticated access policy.

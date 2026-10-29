# SentinelOps operations dashboard

A responsive React + TypeScript dashboard for **read-only** service and incident monitoring, backed by the existing Spring Boot API. This first UI slice includes JWT login, service status, incident status/severity/service filters, pagination, and the first 100 audit events for a selected incident. The incident counts are explicitly scoped to the visible page; the service counts cover the complete service registry response.

## Development

Start PostgreSQL and the Spring Boot API from the repository root (see the root README), then:

```bash
cd web
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to http://127.0.0.1:8080, avoiding CORS and preserving the backend's JWT/RBAC rules. Log in with an existing operator account created through the documented bootstrap/admin flow. The dashboard does not create an admin account automatically.

```bash
npm test
npm run build
```

For production, serve the `web/dist` assets over HTTPS behind a reverse proxy that routes `/api/` to the Spring Boot API **on the same origin**. This is a static SPA, not a separately authenticated backend. Configure secure headers (CSP, HSTS, X-Content-Type-Options) at the proxy. Never expose the dev server publicly.

## Security and operational behavior

- The JWT lives only in React memory: no localStorage, sessionStorage, cookies, or browser URL. Refreshing the page requires signing in again. The password is never persisted.
- Backend authorization remains authoritative. A 401 on API requests signs out the UI; 403 and network errors are shown to the operator without clearing the last successful snapshot.
- Requests are abortable on filter changes, pagination, sign-out, and unmount. Data is fetched on login and manual refresh, **not** continuously streamed. Last refresh time is shown.
- Incident pages are capped at 50 results; service and incident data come from separate API requests and do not form a globally atomic snapshot.
- The audit view uses the first 100 events. Cursor-based continuation, status changes, incident creation, and notification delivery monitoring are future UI slices.

The CI dashboard job runs Vitest API/summary tests and a TypeScript-checked production build.


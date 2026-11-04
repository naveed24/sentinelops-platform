# SentinelOps operations dashboard

A responsive React + TypeScript dashboard for service monitoring and authorized incident lifecycle operations, backed by the existing Spring Boot API. The UI includes JWT login, service status, incident status/severity/service filters, pagination, and cursor-paginated audit history for a selected incident. The notification panel shows pending, sending, sent and failed outbox counts and the oldest pending age using a bounded authenticated summary endpoint. A separate read-only delivery activity table supports status filtering and stable keyset pagination without exposing payloads or delivery credentials. ADMIN and RESPONDER operators can acknowledge, mitigate, and resolve incidents with required audit messages; VIEWER accounts remain read-only. The incident counts are explicitly scoped to the visible page; the service counts cover the complete service registry response.

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
- HTTP 401 and 403 responses use fixed, operator-safe messages instead of untrusted proxy or authentication-filter response bodies. HTTP status codes remain available for sign-out and access-denied handling; 400/409 validation and conflict details remain actionable.\n- HTTP 5xx responses show a generic retry/contact-administrator message instead of untrusted server exception text, preventing accidental display of SQL errors, internal paths, or secrets. The HTTP status is retained for application logic; 4xx validation and conflict messages remain actionable. Server-side logs and tracing are the appropriate places for diagnosis.
- Notification health and delivery activity are independently fetched and refreshed on the same manual refresh action. Failed requests retain a clearly marked stale snapshot; they never substitute zero counts. Only aggregate counts and age are returned, not payloads, webhook URLs, lease tokens, or error text.
- Requests are abortable on filter changes, pagination, sign-out, and unmount. Data is fetched on login and manual refresh, **not** continuously streamed. Last refresh time is shown.
- Incident pages are capped at 50 results; service and incident data come from separate API requests and do not form a globally atomic snapshot.
- Incident status changes use the backend's transition policy, require a nonblank audit message (max 500 characters), and include the currently displayed incident version. A 409 conflict blocks further edits until the latest data is refreshed. The backend enforces RBAC and transition validity independently of the UI.
- The audit view uses the backend's ascending timestamp/ID keyset cursor (50 events per request). Operators can load additional pages without offset drift, including timelines longer than 100 events. A failed request preserves previously loaded history and offers a retry; switching incidents, signing out, or refreshing after a transition aborts obsolete requests and resets pagination. Newly appended events appear when the operator loads further pages or refreshes the incident. Incident creation and detailed per-notification diagnostics are future UI slices.

The CI dashboard job runs Vitest API/summary tests and a TypeScript-checked production build.


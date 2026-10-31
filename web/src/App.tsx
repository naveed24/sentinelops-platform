import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ApiError, getIncidentHistory, getIncidents, getServices, login,
  type AuditEvent, type Incident, type IncidentFilters, type Session, type ServiceRecord
} from './api';
import { displayTime, statusTone, summarize } from './summary';
import { IncidentActions } from './IncidentActions';
import { NotificationHealth } from './NotificationHealth';

const PAGE_SIZE = 50;
const initialFilters: IncidentFilters = { status: '', severity: '', serviceId: '' };

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred';
}

function Pill({ value }: { value: string }) {
  return <span className={'pill pill-' + statusTone(value)}>{value.replaceAll('_', ' ')}</span>;
}

function Login({ onLogin, notice }: { onLogin: (session: Session) => void; notice: string }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const session = await login(email.trim(), password);
      setPassword('');
      onLogin(session);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-panel" aria-labelledby="login-heading">
        <div className="brand"><span className="brand-mark" aria-hidden="true">S</span> SentinelOps</div>
        <p className="eyebrow">OPERATIONS CONSOLE</p>
        <h1 id="login-heading">Stay ahead of incidents.</h1>
        <p className="muted">Sign in with your SentinelOps operator account to monitor service health and incident activity.</p>
        {notice && <p role="status" className="notice">{notice}</p>}
        {error && <p role="alert" className="error-banner">{error}</p>}
        <form onSubmit={submit} className="login-form">
          <label htmlFor="email">Email address</label>
          <input id="email" type="email" autoComplete="username" required maxLength={254}
            value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" required
            value={password} onChange={(event) => setPassword(event.target.value)} />
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="login-footer">Access is restricted to authorized operators. Sessions are held in memory only.</p>
      </section>
    </main>
  );
}

function Dashboard({ session, onSignOut }: {
  session: Session; onSignOut: (notice?: string) => void;
}) {
  const [services, setServices] = useState<ServiceRecord[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [filters, setFilters] = useState<IncidentFilters>(initialFilters);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [history, setHistory] = useState<AuditEvent[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [historyRevision, setHistoryRevision] = useState(0);
  const [actionNotice, setActionNotice] = useState('');
  const handleNotificationUnauthorized = useCallback(() => {
    onSignOut('Your session expired. Please sign in again.');
  }, [onSignOut]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    Promise.all([
      getServices(session.accessToken, controller.signal),
      getIncidents(session.accessToken, filters, page, PAGE_SIZE, controller.signal)
    ]).then(([nextServices, nextIncidents]) => {
      if (controller.signal.aborted) return;
      setServices(nextServices);
      setIncidents(nextIncidents);
      setUpdatedAt(new Date().toISOString());
    }).catch((failure: unknown) => {
      if (controller.signal.aborted) return;
      if (failure instanceof ApiError && failure.status === 401) {
        onSignOut('Your session expired. Please sign in again.');
      } else {
        setError(errorMessage(failure));
      }
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [session.accessToken, filters.status, filters.severity, filters.serviceId, page, revision, onSignOut]);

  useEffect(() => {
    if (selectedId === null) {
      setHistory([]);
      setHistoryError('');
      return;
    }
    const controller = new AbortController();
    setHistoryLoading(true);
    setHistoryError('');
    setHistory([]);
    getIncidentHistory(session.accessToken, selectedId, controller.signal)
      .then((items) => { if (!controller.signal.aborted) setHistory(items); })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) {
          onSignOut('Your session expired. Please sign in again.');
        } else {
          setHistoryError(errorMessage(failure));
        }
      }).finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, session.accessToken, onSignOut, historyRevision]);

  const stats = useMemo(() => summarize(incidents, services), [incidents, services]);
  const serviceNames = useMemo(() => new Map(services.map((service) => [service.id, service.name])), [services]);
  const selectedIncident = incidents.find((incident) => incident.id === selectedId);

  function changeFilter<K extends keyof IncidentFilters>(name: K, value: IncidentFilters[K]) {
    setFilters((previous) => ({ ...previous, [name]: value }));
    setPage(0);
    setSelectedId(null);
    setActionNotice('');
  }

  function changePage(next: number) {
    setPage(next);
    setSelectedId(null);
    setActionNotice('');
  }

  function handleTransition(next: Incident) {
    setIncidents((current) => current.map((item) => item.id === next.id ? next : item));
    setActionNotice('Status updated to ' + next.status + '. Refreshing the incident and audit history.');
    setHistoryRevision((value) => value + 1);
    setRevision((value) => value + 1);
  }

  function handleStaleTransition() {
    setActionNotice('This incident was changed by another operator. Refreshing its latest status and history.');
    setHistoryRevision((value) => value + 1);
    setRevision((value) => value + 1);
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark" aria-hidden="true">S</span> SentinelOps</div>
        <div className="nav-heading">WORKSPACE</div>
        <a className="nav-item active" href="#overview">Overview</a>
        <a className="nav-item" href="#services">Services</a>
        <a className="nav-item" href="#incidents">Incidents</a>
        <a className="nav-item" href="#notifications">Notifications</a>
        <div className="sidebar-bottom">
          <span className="eyebrow">SIGNED IN AS</span>
          <strong>{session.user.displayName}</strong>
          <span className="muted small">{session.user.role} · {session.user.email}</span>
          <button className="text-button" onClick={() => onSignOut()}>Sign out</button>
        </div>
      </aside>
      <main className="workspace" id="overview">
        <header className="topbar">
          <div>
            <p className="eyebrow">OPERATIONS / OVERVIEW</p>
            <h1>Command center</h1>
            <p className="muted">Live API snapshot of services and incidents. Counts reflect the current incident page.</p>
          </div>
          <button className="secondary-button" onClick={() => setRevision((value) => value + 1)}
            disabled={loading}>↻ Refresh data</button>
        </header>
        <div className="snapshot-line" aria-live="polite">
          {loading ? 'Loading latest data…' : updatedAt ? 'Last refreshed ' + displayTime(updatedAt) : 'No snapshot yet'}
          <span> · Manual refresh</span>
        </div>
        {error && <div role="alert" className="error-banner">Unable to refresh data: {error}. Showing the last successful snapshot.</div>}
        <section className="stats-grid" aria-label="Overview statistics">
          <div className="stat-card"><span>Incidents on page</span><strong>{stats.visibleIncidents}</strong><small>Page {page + 1} · max {PAGE_SIZE}</small></div>
          <div className="stat-card"><span>Unresolved on page</span><strong>{stats.activeIncidents}</strong><small>OPEN / ACKNOWLEDGED / MITIGATED</small></div>
          <div className="stat-card"><span>SEV1 unresolved</span><strong>{stats.criticalIncidents}</strong><small>Current incident page</small></div>
          <div className="stat-card"><span>Degraded services</span><strong>{stats.degradedServices}<em> / {stats.totalServices}</em></strong><small>Across all registered services</small></div>
        </section>
        <section className="section" id="services" aria-labelledby="services-heading">
          <div className="section-title"><div><p className="eyebrow">SERVICE REGISTRY</p><h2 id="services-heading">Service health</h2></div><span className="muted small">{services.length} registered</span></div>
          {services.length === 0 && !loading ? <p className="empty-state">No services registered yet.</p> :
            <div className="service-grid">
              {services.map((service) => (
                <article className="service-card" key={service.id}>
                  <div className="service-head"><strong>{service.name}</strong><Pill value={service.status} /></div>
                  <p className="muted small">{service.description || 'No description provided'}</p>
                  <span className="service-owner">Team: {service.ownerTeam}</span>
                </article>
              ))}
            </div>}
        </section>
        <section className="section" id="incidents" aria-labelledby="incidents-heading">
          <div className="section-title"><div><p className="eyebrow">INCIDENT MANAGEMENT</p><h2 id="incidents-heading">Incident feed</h2></div><span className="muted small">Newest first · {PAGE_SIZE} per page</span></div>
          <div className="filter-bar">
            <label>Status
              <select value={filters.status} onChange={(event) => changeFilter('status', event.target.value as IncidentFilters['status'])}>
                <option value="">All statuses</option>
                <option value="OPEN">Open</option><option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="MITIGATED">Mitigated</option><option value="RESOLVED">Resolved</option>
              </select>
            </label>
            <label>Severity
              <select value={filters.severity} onChange={(event) => changeFilter('severity', event.target.value as IncidentFilters['severity'])}>
                <option value="">All severities</option>
                <option value="SEV1">SEV1</option><option value="SEV2">SEV2</option>
                <option value="SEV3">SEV3</option><option value="SEV4">SEV4</option>
              </select>
            </label>
            <label>Service
              <select value={filters.serviceId} onChange={(event) => changeFilter('serviceId', event.target.value)}>
                <option value="">All services</option>
                {services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
              </select>
            </label>
          </div>
          <div className="table-scroll">
            <table>
              <thead><tr><th>Incident</th><th>Service</th><th>Severity</th><th>Status</th><th>Created</th></tr></thead>
              <tbody>
                {incidents.map((incident) => (
                  <tr key={incident.id} className={selectedId === incident.id ? 'selected-row' : ''}>
                    <td><button className="incident-link" aria-pressed={selectedId === incident.id}
                      onClick={() => { setActionNotice(''); setSelectedId((id) => id === incident.id ? null : incident.id); }}>
                      <span className="incident-id">INC-{incident.id}</span>{incident.title}
                    </button></td>
                    <td>{serviceNames.get(incident.serviceId) ?? 'Service #' + incident.serviceId}</td>
                    <td><Pill value={incident.severity} /></td>
                    <td><Pill value={incident.status} /></td>
                    <td className="muted small">{displayTime(incident.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {incidents.length === 0 && !loading && !error && <p className="empty-state">No incidents match these filters.</p>}
          </div>
          <div className="pagination">
            <span className="muted small">Page {page + 1} · {incidents.length} results</span>
            <div className="pagination-actions">
              <button className="secondary-button" disabled={page === 0 || loading}
                onClick={() => changePage(page - 1)}>Previous</button>
              <button className="secondary-button" disabled={incidents.length < PAGE_SIZE || loading}
                onClick={() => changePage(page + 1)}>Next</button>
            </div>
          </div>
          {selectedIncident && (
            <section className="history-panel" aria-labelledby="history-heading">
              <div className="section-title">
                <div><p className="eyebrow">AUDIT TRAIL</p><h3 id="history-heading">INC-{selectedIncident.id}: {selectedIncident.title}</h3></div>
                <button className="text-button" onClick={() => setSelectedId(null)}>Close</button>
              </div>
              <p className="muted small">{selectedIncident.description || 'No description'} · First 100 history events</p>
              {actionNotice && <p role="status" className="notice">{actionNotice}</p>}
              <IncidentActions key={selectedIncident.id + ':' + selectedIncident.version}
                incident={selectedIncident} token={session.accessToken} role={session.user.role}
                onUpdated={handleTransition} onStale={handleStaleTransition}
                onUnauthorized={() => onSignOut('Your session expired. Please sign in again.')} />
              {historyLoading && <p role="status" className="muted">Loading audit history…</p>}
              {historyError && <p role="alert" className="error-banner">{historyError}</p>}
              {!historyLoading && !historyError && history.length === 0 && <p className="empty-state">No history events found.</p>}
              <ol className="timeline">
                {history.map((event) => <li key={event.id}>
                  <div><Pill value={event.toStatus} /> <span className="muted small">{displayTime(event.createdAt)}</span></div>
                  <p>{event.message}</p>
                  <small className="muted">{event.actorEmail || 'System'}{event.fromStatus ? ' · from ' + event.fromStatus : ' · created'}</small>
                </li>)}
              </ol>
            </section>
          )}
        </section>
        <NotificationHealth token={session.accessToken} revision={revision}
          onUnauthorized={handleNotificationUnauthorized} />
        <footer className="footer">SentinelOps · Incident operations dashboard · API v1</footer>
      </main>
    </div>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [notice, setNotice] = useState('');
  const signOut = useCallback((message?: string) => {
    setSession(null);
    setNotice(message || '');
  }, []);
  return session
    ? <Dashboard session={session} onSignOut={signOut} />
    : <Login notice={notice} onLogin={(next) => { setNotice(''); setSession(next); }} />;
}


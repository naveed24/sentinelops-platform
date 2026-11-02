import { useEffect, useState } from 'react';
import { ApiError, getIncidentHistoryPage, type AuditCursor, type AuditEvent } from './api';
import { appendAuditPage, nextAuditCursor } from './historyCursor';
import { displayTime, statusTone } from './summary';

const HISTORY_PAGE_SIZE = 50;

export function IncidentHistory({ token, incidentId, onUnauthorized }: {
  token: string;
  incidentId: number;
  onUnauthorized: () => void;
}) {
  const [items, setItems] = useState<AuditEvent[]>([]);
  const [cursor, setCursor] = useState<AuditCursor | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getIncidentHistoryPage(token, incidentId, cursor, HISTORY_PAGE_SIZE, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) return;
        setItems((previous) => cursor === null ? page : appendAuditPage(previous, page));
        setHasMore(page.length === HISTORY_PAGE_SIZE);
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) {
          onUnauthorized();
        } else {
          setError(failure instanceof Error ? failure.message : 'Unable to load audit history');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [token, incidentId, cursor, retry, onUnauthorized]);

  function loadMore() {
    if (loading || error || !hasMore) return;
    const next = nextAuditCursor(items);
    if (next !== null) setCursor(next);
  }

  return (
    <div aria-label="Incident audit history">
      <p className="muted small" aria-live="polite">
        Showing {items.length} audit events · oldest first · {HISTORY_PAGE_SIZE} per request
      </p>
      {loading && <p role="status" className="muted">Loading audit history…</p>}
      {error && (
        <div role="alert" className="error-banner">
          Unable to load audit history: {error}. Previously loaded events are preserved.
          <button type="button" className="secondary-button" onClick={() => setRetry((value) => value + 1)}>
            Retry history request
          </button>
        </div>
      )}
      {!loading && !error && items.length === 0 && <p className="empty-state">No history events found.</p>}
      <ol className="timeline">
        {items.map((event) => (
          <li key={event.id}>
            <div><span className={'pill pill-' + statusTone(event.toStatus)}>{event.toStatus.replaceAll('_', ' ')}</span>{' '}
              <span className="muted small">{displayTime(event.createdAt)}</span></div>
            <p>{event.message}</p>
            <small className="muted">
              {event.actorEmail || 'System'}{event.fromStatus ? ' · from ' + event.fromStatus : ' · created'}
            </small>
          </li>
        ))}
      </ol>
      {hasMore && items.length > 0 && (
        <button type="button" className="secondary-button" disabled={loading || Boolean(error)}
          onClick={loadMore}>
          {loading ? 'Loading…' : 'Load more history'}
        </button>
      )}
      {!hasMore && items.length > 0 && <p className="muted small">All available history events loaded.</p>}
    </div>
  );
}

import { useEffect, useRef, useState } from 'react';
import {
  ApiError, getNotificationPage, type NotificationItem, type NotificationStatus
} from './api';
import { displayTime, statusTone } from './summary';

const PAGE_LIMIT = 25;

function failureMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unable to load notification activity';
}

export function NotificationActivity({ token, revision, onUnauthorized }: {
  token: string; revision: number; onUnauthorized: () => void;
}) {
  const [status, setStatus] = useState<NotificationStatus | ''>('');
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [nextBeforeId, setNextBeforeId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const [pageError, setPageError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const olderRequest = useRef<AbortController | null>(null);

  useEffect(() => {
    olderRequest.current?.abort();
    olderRequest.current = null;
    const controller = new AbortController();
    setLoading(true);
    setLoadingMore(false);
    setError('');
    setPageError('');
    getNotificationPage(token, status, null, PAGE_LIMIT, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) return;
        setItems(page.items);
        setNextBeforeId(page.nextBeforeId);
        setUpdatedAt(new Date().toISOString());
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) {
          onUnauthorized();
        } else {
          setError(failureMessage(failure));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => {
      controller.abort();
      olderRequest.current?.abort();
    };
  }, [token, status, revision, onUnauthorized]);

  function changeStatus(value: NotificationStatus | '') {
    olderRequest.current?.abort();
    setItems([]);
    setNextBeforeId(null);
    setUpdatedAt(null);
    setError('');
    setPageError('');
    setStatus(value);
  }

  async function loadOlder() {
    if (nextBeforeId === null || loading || loadingMore) return;
    olderRequest.current?.abort();
    const controller = new AbortController();
    olderRequest.current = controller;
    setLoadingMore(true);
    setPageError('');
    try {
      const page = await getNotificationPage(token, status, nextBeforeId, PAGE_LIMIT, controller.signal);
      if (controller.signal.aborted) return;
      setItems((previous) => {
        const seen = new Set(previous.map((item) => item.id));
        return [...previous, ...page.items.filter((item) => !seen.has(item.id))];
      });
      setNextBeforeId(page.nextBeforeId);
    } catch (failure) {
      if (controller.signal.aborted) return;
      if (failure instanceof ApiError && failure.status === 401) {
        onUnauthorized();
      } else {
        setPageError(failureMessage(failure));
      }
    } finally {
      if (!controller.signal.aborted) setLoadingMore(false);
    }
  }

  return (
    <section className="notification-activity" aria-labelledby="notification-activity-heading">
      <div className="section-title">
        <div>
          <p className="eyebrow">OUTBOX AUDIT</p>
          <h3 id="notification-activity-heading">Delivery activity</h3>
        </div>
        <span className="muted small">Newest first · {PAGE_LIMIT} per page</span>
      </div>
      <div className="filter-bar">
        <label htmlFor="notification-status">Delivery status
          <select id="notification-status" value={status}
            onChange={(event) => changeStatus(event.target.value as NotificationStatus | '')}>
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="SENDING">Sending</option>
            <option value="SENT">Sent</option>
            <option value="FAILED">Failed</option>
          </select>
        </label>
      </div>
      {loading && <p role="status" className="muted small">Refreshing delivery activity…</p>}
      {error && <p role="alert" className="error-banner">
        Unable to refresh delivery activity: {error}.
        {updatedAt ? ' Showing the last successful snapshot.' : ''}
      </p>}
      <div className="table-scroll">
        <table>
          <thead>
            <tr><th>Notification</th><th>Service / rule</th><th>Event</th><th>Status</th><th>Attempts</th><th>Created</th><th>Next / delivered</th></tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td className="incident-id">NOT-{item.id}</td>
                <td><strong>{item.serviceName}</strong><div className="muted small">{item.ruleName}</div></td>
                <td>{item.eventType} <span className={'pill pill-' + statusTone(item.severity)}>{item.severity}</span></td>
                <td><span className={'pill pill-' + statusTone(item.status)}>{item.status}</span></td>
                <td>{item.attemptCount}</td>
                <td className="muted small">{displayTime(item.createdAt)}</td>
                <td className="muted small">
                  {item.status === 'SENT' ? displayTime(item.deliveredAt) :
                    item.status === 'FAILED' ? 'No further attempts' : displayTime(item.nextAttemptAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && !error && items.length === 0 &&
          <p className="empty-state">No notifications match this status.</p>}
      </div>
      {pageError && <p role="alert" className="error-banner">
        Could not load older deliveries: {pageError}. Retry using Load older.
      </p>}
      <div className="pagination">
        <span className="muted small">
          {items.length} displayed{updatedAt ? ' · Refreshed ' + displayTime(updatedAt) : ''}
          {error ? ' · Stale data' : ''}
        </span>
        {nextBeforeId !== null &&
          <button className="secondary-button" disabled={loading || loadingMore || !!error}
            onClick={loadOlder}>{loadingMore ? 'Loading…' : 'Load older'}</button>}
      </div>
    </section>
  );
}

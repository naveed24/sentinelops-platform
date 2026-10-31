import { useEffect, useState } from 'react';
import { ApiError, getNotificationSummary, type NotificationSummary } from './api';
import { displayTime } from './summary';

export function pendingAgeLabel(snapshot: NotificationSummary): string {
  if (snapshot.pending === 0) return 'No pending deliveries';
  const seconds = Math.max(0, Math.floor(snapshot.oldestPendingAgeSeconds));
  if (seconds < 60) return 'Oldest pending: ' + seconds + 's';
  if (seconds < 3600) return 'Oldest pending: ' + Math.floor(seconds / 60) + 'm ' + (seconds % 60) + 's';
  return 'Oldest pending: ' + Math.floor(seconds / 3600) + 'h ' + Math.floor((seconds % 3600) / 60) + 'm';
}

export function NotificationHealth({ token, revision, onUnauthorized }: {
  token: string; revision: number; onUnauthorized: () => void;
}) {
  const [snapshot, setSnapshot] = useState<NotificationSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getNotificationSummary(token, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setSnapshot(result);
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) {
          onUnauthorized();
        } else {
          setError(failure instanceof Error ? failure.message : 'Unable to load notification status');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [token, revision, onUnauthorized]);

  return (
    <section className="section" id="notifications" aria-labelledby="notifications-heading">
      <div className="section-title">
        <div><p className="eyebrow">DELIVERY HEALTH</p><h2 id="notifications-heading">Notifications</h2></div>
        <span className="muted small">Database snapshot · manual refresh</span>
      </div>
      {loading && <p role="status" className="muted small">Loading notification health…</p>}
      {error && <p role="alert" className="error-banner">
        Unable to refresh notification health: {error}.
        {snapshot ? ' Showing the last successful snapshot.' : ''}
      </p>}
      {snapshot ? (
        <>
          <div className="notification-grid" aria-label="Notification delivery counts">
            <div className="stat-card"><span>Pending</span><strong>{snapshot.pending}</strong><small>Waiting for a delivery attempt</small></div>
            <div className="stat-card"><span>Sending</span><strong>{snapshot.sending}</strong><small>Currently leased by workers</small></div>
            <div className="stat-card"><span>Sent</span><strong>{snapshot.sent}</strong><small>Delivered successfully</small></div>
            <div className="stat-card"><span>Failed</span><strong>{snapshot.failed}</strong><small>Terminal delivery failures</small></div>
          </div>
          <p className="muted small notification-meta">
            {pendingAgeLabel(snapshot)} · Sampled {displayTime(snapshot.sampledAt)}
            {error ? ' · Stale data' : ''}
          </p>
        </>
      ) : !loading && !error ? <p className="empty-state">No notification snapshot available.</p> : null}
    </section>
  );
}

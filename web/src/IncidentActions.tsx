import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  ApiError, transitionIncident, type Incident, type IncidentStatus, type Operator
} from './api';
import { allowedTransitions } from './transitions';

interface Props {
  incident: Incident;
  token: string;
  role: Operator['role'];
  onUpdated: (incident: Incident) => void;
  onStale: () => void;
  onUnauthorized: () => void;
}

/** Backend authorization and optimistic locking remain authoritative. */
export function IncidentActions({ incident, token, role, onUpdated, onStale, onUnauthorized }: Props) {
  const options = allowedTransitions(incident.status);
  const [status, setStatus] = useState<IncidentStatus>(options[0] ?? incident.status);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef<AbortController | null>(null);

  useEffect(() => () => pending.current?.abort(), []);

  if (role === 'VIEWER') {
    return <p className="muted small">Your viewer role has read-only access to incident history.</p>;
  }
  if (options.length === 0) {
    return <p className="muted small">This incident is resolved. No further transitions are permitted.</p>;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = message.trim();
    if (pending.current || stale || !reason || reason.length > 500 || !options.includes(status)) return;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setError('');
    try {
      const updated = await transitionIncident(token, incident.id, status, reason, incident.version, controller.signal);
      if (!controller.signal.aborted) onUpdated(updated);
    } catch (failure) {
      if (controller.signal.aborted) return;
      if (failure instanceof ApiError && failure.status === 401) {
        onUnauthorized();
      } else if (failure instanceof ApiError && failure.status === 409) {
        setStale(true);
        setError('Another operator changed this incident. Wait for the latest version before retrying.');
        onStale();
      } else {
        setError(failure instanceof Error ? failure.message : 'Unable to change incident status');
      }
    } finally {
      if (pending.current === controller) pending.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <form className="transition-form" onSubmit={submit} aria-label="Change incident status">
      <div className="transition-fields">
        <label>Next status
          <select value={status} onChange={(event) => setStatus(event.target.value as IncidentStatus)}
            disabled={busy || stale}>
            {options.map((option) => <option key={option} value={option}>{option.replaceAll('_', ' ')}</option>)}
          </select>
        </label>
        <label>Audit message
          <textarea required maxLength={500} rows={2} value={message}
            placeholder="Explain why this status is changing"
            onChange={(event) => setMessage(event.target.value)}
            disabled={busy || stale} />
        </label>
      </div>
      {error && <p role="alert" className="error-banner">{error}</p>}
      <button className="primary-button" type="submit" disabled={busy || stale || !message.trim()}>
        {busy ? 'Saving…' : 'Update status'}
      </button>
    </form>
  );
}

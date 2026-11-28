import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  ApiError, createIncident, type Incident, type IncidentSeverity,
  type Operator, type ServiceRecord
} from './api';

interface Props {
  token: string;
  role: Operator['role'];
  services: ServiceRecord[];
  onCreated: (incident: Incident) => void;
  onUnauthorized: () => void;
}

/** Client-side checks complement (never replace) the backend's JWT/RBAC and validation. */
export function IncidentCreate({ token, role, services, onCreated, onUnauthorized }: Props) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<IncidentSeverity>('SEV3');
  const [serviceId, setServiceId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef<AbortController | null>(null);

  useEffect(() => () => pending.current?.abort(), [token]);

  if (role === 'VIEWER') return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const cleanedTitle = title.trim();
    const id = Number(serviceId);
    if (pending.current) return;
    if (!cleanedTitle || cleanedTitle.length > 180 ||
        description.length > 2000 ||
        !Number.isSafeInteger(id) || id <= 0 ||
        !services.some((service) => service.id === id)) {
      setError('Enter a title (1–180 characters), a valid service, and a description under 2000 characters.');
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setError('');
    try {
      const created = await createIncident(token, {
        title: cleanedTitle,
        description: description.trim(),
        severity,
        serviceId: id
      }, controller.signal);
      if (!controller.signal.aborted) {
        setTitle('');
        setDescription('');
        setServiceId('');
        setSeverity('SEV3');
        onCreated(created);
      }
    } catch (failure) {
      if (controller.signal.aborted) return;
      if (failure instanceof ApiError && failure.status === 401) {
        onUnauthorized();
      } else {
        // Preserve user inputs on failure so operators can correct/retry.
        setError(failure instanceof Error ? failure.message : 'Unable to create incident.');
      }
    } finally {
      if (pending.current === controller) pending.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <form className="incident-create-form" aria-label="Report new incident" onSubmit={submit}>
      <div className="incident-create-heading">
        <strong>Report a new incident</strong>
        <span className="muted small">Authorized responders and administrators only</span>
      </div>
      <div className="incident-create-fields">
        <label>Title
          <input required maxLength={180} value={title} disabled={busy}
            placeholder="Briefly describe the impact"
            onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label>Affected service
          <select required value={serviceId} disabled={busy || services.length === 0}
            onChange={(event) => setServiceId(event.target.value)}>
            <option value="">Select service</option>
            {services.map((service) => (
              <option key={service.id} value={service.id}>{service.name}</option>
            ))}
          </select>
        </label>
        <label>Severity
          <select value={severity} disabled={busy}
            onChange={(event) => setSeverity(event.target.value as IncidentSeverity)}>
            <option value="SEV1">SEV1 — Critical</option>
            <option value="SEV2">SEV2 — High</option>
            <option value="SEV3">SEV3 — Moderate</option>
            <option value="SEV4">SEV4 — Low</option>
          </select>
        </label>
        <label className="incident-description">Description (optional)
          <textarea maxLength={2000} rows={2} value={description} disabled={busy}
            placeholder="Add context, symptoms, and known impact"
            onChange={(event) => setDescription(event.target.value)} />
        </label>
      </div>
      {error && <p role="alert" className="error-banner">{error}</p>}
      <button type="submit" className="primary-button" disabled={busy || !title.trim() || !serviceId}>
        {busy ? 'Creating…' : 'Create incident'}
      </button>
    </form>
  );
}

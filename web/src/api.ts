export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'MITIGATED' | 'RESOLVED';
export type IncidentSeverity = 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
export type ServiceStatus = 'ACTIVE' | 'DEGRADED' | 'MAINTENANCE' | 'RETIRED';

export interface Operator {
  id: number;
  displayName: string;
  email: string;
  role: 'ADMIN' | 'RESPONDER' | 'VIEWER';
}

export interface Session {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: Operator;
}

export interface ServiceRecord {
  id: number;
  name: string;
  description: string | null;
  ownerTeam: string;
  status: ServiceStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Incident {
  id: number;
  title: string;
  description: string | null;
  severity: IncidentSeverity;
  status: IncidentStatus;
  serviceId: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface NotificationSummary {
  sampledAt: string;
  pending: number;
  sending: number;
  sent: number;
  failed: number;
  oldestPendingAgeSeconds: number;
}

export function getNotificationSummary(
  token: string, signal?: AbortSignal
): Promise<NotificationSummary> {
  return request<NotificationSummary>('/api/v1/notifications/summary', { token, signal });
}

export type NotificationStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED';

export interface NotificationItem {
  id: number;
  status: NotificationStatus;
  ruleId: number;
  ruleName: string;
  serviceId: number;
  serviceName: string;
  eventType: string;
  severity: IncidentSeverity;
  attemptCount: number;
  createdAt: string;
  nextAttemptAt: string;
  deliveredAt: string | null;
}

export interface NotificationPage {
  items: NotificationItem[];
  nextBeforeId: number | null;
}

/** Stable descending-id keyset cursor: new deliveries never shift older pages. */
export function notificationQuery(
  status: NotificationStatus | '', beforeId: number | null, limit = 25
): string {
  const query = new URLSearchParams({ limit: String(limit) });
  if (status) query.set('status', status);
  if (beforeId !== null) query.set('beforeId', String(beforeId));
  return '/api/v1/notifications?' + query.toString();
}

export function getNotificationPage(
  token: string, status: NotificationStatus | '', beforeId: number | null,
  limit = 25, signal?: AbortSignal
): Promise<NotificationPage> {
  return request<NotificationPage>(notificationQuery(status, beforeId, limit), { token, signal });
}

export interface AuditEvent {
  id: number;
  fromStatus: IncidentStatus | null;
  toStatus: IncidentStatus;
  message: string;
  createdAt: string;
  actorUserId: number | null;
  actorEmail: string | null;
}

export interface IncidentFilters {
  status: IncidentStatus | '';
  severity: IncidentSeverity | '';
  serviceId: string;
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(
  path: string,
  options: { token?: string; method?: string; body?: unknown; signal?: AbortSignal } = {}
): Promise<T> {
  const response = await fetch(path, {
    method: options.method ?? 'GET',
    signal: options.signal,
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(options.token ? { Authorization: 'Bearer ' + options.token } : {})
    },
    ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) })
  });
  if (!response.ok) {
    let message = 'Request failed (' + response.status + ')';
    try {
      const error: unknown = await response.json();
      if (error && typeof error === 'object' && 'message' in error &&
          typeof error.message === 'string' && error.message.trim()) {
        message = error.message;
      }
    } catch {
      // Spring Security may return a non-JSON 401/403 response.
    }
    throw new ApiError(response.status, message);
  }
  return response.json() as Promise<T>;
}

export function login(email: string, password: string): Promise<Session> {
  return request<Session>('/api/v1/auth/login', {
    method: 'POST',
    body: { email, password }
  });
}

export function getServices(token: string, signal?: AbortSignal): Promise<ServiceRecord[]> {
  return request<ServiceRecord[]>('/api/v1/services', { token, signal });
}

export function incidentQuery(filters: IncidentFilters, page: number, limit: number): string {
  const query = new URLSearchParams({ page: String(page), limit: String(limit) });
  if (filters.status) query.set('status', filters.status);
  if (filters.severity) query.set('severity', filters.severity);
  if (filters.serviceId) query.set('serviceId', filters.serviceId);
  return '/api/v1/incidents?' + query.toString();
}

export function getIncidents(
  token: string, filters: IncidentFilters, page: number, limit: number, signal?: AbortSignal
): Promise<Incident[]> {
  return request<Incident[]>(incidentQuery(filters, page, limit), { token, signal });
}

export function getIncidentHistory(
  token: string, id: number, signal?: AbortSignal
): Promise<AuditEvent[]> {
  return request<AuditEvent[]>('/api/v1/incidents/' + id + '/history?page=0&size=100', {
    token, signal
  });
}


/** Optimistic concurrency is required for operator-driven lifecycle transitions. */
export function transitionIncident(
  token: string, id: number, status: IncidentStatus, message: string,
  expectedVersion: number, signal?: AbortSignal
): Promise<Incident> {
  return request<Incident>('/api/v1/incidents/' + id + '/status', {
    token, method: 'PATCH', signal,
    body: { status, message, expectedVersion }
  });
}

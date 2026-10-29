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


import type { Incident, ServiceRecord } from './api';

export function summarize(incidents: Incident[], services: ServiceRecord[]) {
  return {
    visibleIncidents: incidents.length,
    activeIncidents: incidents.filter((incident) => incident.status !== 'RESOLVED').length,
    criticalIncidents: incidents.filter((incident) =>
      incident.status !== 'RESOLVED' && incident.severity === 'SEV1').length,
    degradedServices: services.filter((service) => service.status === 'DEGRADED').length,
    totalServices: services.length
  };
}

export function displayTime(value: string | null): string {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? 'Unknown' : parsed.toLocaleString();
}

export function statusTone(value: string): string {
  switch (value) {
    case 'SEV1':
    case 'OPEN':
    case 'FAILED':
    case 'DEGRADED':
      return 'danger';
    case 'SEV2':
    case 'ACKNOWLEDGED':
    case 'PENDING':
    case 'MAINTENANCE':
      return 'warning';
    case 'SEV3':
    case 'MITIGATED':
    case 'SENDING':
      return 'info';
    case 'SEV4':
    case 'RESOLVED':
    case 'ACTIVE':
    case 'SENT':
      return 'success';
    default:
      return 'neutral';
  }
}


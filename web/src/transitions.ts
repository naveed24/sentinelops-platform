import type { IncidentStatus } from './api';

/** Mirrors the incident lifecycle policy enforced by the Spring Boot service. */
export function allowedTransitions(status: IncidentStatus): readonly IncidentStatus[] {
  switch (status) {
    case 'OPEN': return ['ACKNOWLEDGED', 'MITIGATED'];
    case 'ACKNOWLEDGED': return ['MITIGATED'];
    case 'MITIGATED': return ['RESOLVED'];
    case 'RESOLVED': return [];
  }
}

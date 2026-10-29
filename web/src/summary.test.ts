import { describe, expect, it } from 'vitest';
import type { Incident, ServiceRecord } from './api';
import { displayTime, statusTone, summarize } from './summary';

const incident = (status: Incident['status'], severity: Incident['severity']) =>
  ({ status, severity } as Incident);
const service = (status: ServiceRecord['status']) => ({ status } as ServiceRecord);

describe('dashboard summaries', () => {
  it('counts only visible incidents and all loaded service statuses', () => {
    expect(summarize([
      incident('OPEN', 'SEV1'), incident('ACKNOWLEDGED', 'SEV2'),
      incident('MITIGATED', 'SEV1'), incident('RESOLVED', 'SEV1')
    ], [service('ACTIVE'), service('DEGRADED'), service('MAINTENANCE')]))
      .toEqual({ visibleIncidents: 4, activeIncidents: 3, criticalIncidents: 2,
        degradedServices: 1, totalServices: 3 });
  });

  it('handles empty snapshots and unknown dates', () => {
    expect(summarize([], []).activeIncidents).toBe(0);
    expect(displayTime(null)).toBe('—');
    expect(displayTime('invalid-date')).toBe('Unknown');
  });

  it('uses fixed presentation tones for known statuses', () => {
    expect(statusTone('SEV1')).toBe('danger');
    expect(statusTone('ACTIVE')).toBe('success');
    expect(statusTone('UNEXPECTED')).toBe('neutral');
  });
});


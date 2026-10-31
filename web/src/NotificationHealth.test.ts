import { describe, expect, it } from 'vitest';
import { pendingAgeLabel } from './NotificationHealth';
import type { NotificationSummary } from './api';

const sample: NotificationSummary = {
  sampledAt: '2026-10-31T00:00:00Z',
  pending: 1, sending: 0, sent: 0, failed: 0, oldestPendingAgeSeconds: 0
};

describe('notification backlog presentation', () => {
  it('distinguishes an empty backlog from a zero-second pending notification', () => {
    expect(pendingAgeLabel({ ...sample, pending: 0 })).toBe('No pending deliveries');
    expect(pendingAgeLabel(sample)).toBe('Oldest pending: 0s');
  });
  it('formats backlog age in seconds, minutes and hours', () => {
    expect(pendingAgeLabel({ ...sample, oldestPendingAgeSeconds: 59 })).toBe('Oldest pending: 59s');
    expect(pendingAgeLabel({ ...sample, oldestPendingAgeSeconds: 125 })).toBe('Oldest pending: 2m 5s');
    expect(pendingAgeLabel({ ...sample, oldestPendingAgeSeconds: 3720 })).toBe('Oldest pending: 1h 2m');
  });
});

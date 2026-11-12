import { afterEach, describe, expect, it, vi } from 'vitest';
import { getNotificationPage, notificationQuery } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('notification activity API', () => {
  it('builds bounded status and keyset queries without empty parameters', () => {
    expect(notificationQuery('', null)).toBe('/api/v1/notifications?limit=25');
    expect(notificationQuery('FAILED', 123, 50))
      .toBe('/api/v1/notifications?limit=50&status=FAILED&beforeId=123');
  });

  it('fetches read-only metadata with bearer auth, no cache and abort support', async () => {
    const page = { items: [{
      id: 123, status: 'FAILED', ruleId: 2, ruleName: 'error rate',
      serviceId: 1, serviceName: 'payments', eventType: 'ERROR_RATE',
      severity: 'SEV2', attemptCount: 3, createdAt: '2026-11-01T00:00:00Z',
      nextAttemptAt: '2026-11-01T00:05:00Z', deliveredAt: null
    }], nextBeforeId: null };
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(page), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await getNotificationPage('token', 'FAILED', 123, 25, controller.signal)).toEqual(page);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/notifications?limit=25&status=FAILED&beforeId=123');
    expect(options.headers.Authorization).toBe('Bearer token');
    expect(options.cache).toBe('no-store');
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal).not.toBe(controller.signal);
    controller.abort();
    expect(options.signal.aborted).toBe(true);
    expect(options.method).toBe('GET');
    expect(options.body).toBeUndefined();
  });
}
);

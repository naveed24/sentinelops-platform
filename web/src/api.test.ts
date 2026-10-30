import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getIncidentHistory, getIncidents, getServices, incidentQuery, login, transitionIncident } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('authenticated API client', () => {
  it('posts credentials without authorization headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      accessToken: 'jwt', tokenType: 'Bearer', expiresAt: '2026-10-29T12:00:00Z',
      user: { id: 1, displayName: 'Operator', email: 'op@example.com', role: 'VIEWER' }
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const result = await login('op@example.com', 'a-password');
    expect(result.accessToken).toBe('jwt');
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/auth/login');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBeUndefined();
    expect(JSON.parse(options.body)).toEqual({ email: 'op@example.com', password: 'a-password' });
  });

  it('adds bearer authorization to service requests and forwards cancellation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await getServices('secret', controller.signal)).toEqual([]);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/services');
    expect(options.headers.Authorization).toBe('Bearer secret');
    expect(options.signal).toBe(controller.signal);
    expect(options.cache).toBe('no-store');
  });

  it('serializes filters and paging using the backend contract', async () => {
    expect(incidentQuery({ status: 'OPEN', severity: 'SEV1', serviceId: '42' }, 2, 50))
      .toBe('/api/v1/incidents?page=2&limit=50&status=OPEN&severity=SEV1&serviceId=42');
    const fetchMock = vi.fn().mockImplementation(async () => new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await getIncidents('token', { status: '', severity: '', serviceId: '' }, 0, 50);
    await getIncidentHistory('token', 19);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/incidents?page=0&limit=50');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/incidents/19/history?page=0&size=100');
  });

  it('PATCHes status with the incident version and a caller-controlled cancellation signal', async () => {
    const updated = { id: 19, status: 'MITIGATED', version: 4 };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(updated), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await transitionIncident('secret', 19, 'MITIGATED', 'Error rate recovered', 3, controller.signal))
      .toEqual(updated);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/incidents/19/status');
    expect(options.method).toBe('PATCH');
    expect(options.headers.Authorization).toBe('Bearer secret');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(options.signal).toBe(controller.signal);
    expect(JSON.parse(options.body)).toEqual({
      status: 'MITIGATED', message: 'Error rate recovered', expectedVersion: 3
    });
  });

  it('preserves stale-version conflict errors for a safe UI refresh', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Incident was modified; fetch the latest version and retry'
    }), { status: 409 })));
    await expect(transitionIncident('secret', 19, 'RESOLVED', 'Fixed', 2)).rejects.toMatchObject({
      status: 409, message: 'Incident was modified; fetch the latest version and retry'
    });
  });

  it('reports JSON errors and non-JSON security responses', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Invalid credentials' }), { status: 401 }))
      .mockResolvedValueOnce(new Response('Forbidden', { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(login('op@example.com', 'wrong')).rejects.toMatchObject({
      status: 401, message: 'Invalid credentials'
    });
    await expect(getServices('token')).rejects.toEqual(new ApiError(403, 'Request failed (403)'));
  });
});


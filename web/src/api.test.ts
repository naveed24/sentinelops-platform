import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getNotificationSummary, getIncidentHistory, getIncidentHistoryPage, getIncidents, getServices, incidentHistoryCursorQuery, incidentQuery, login, transitionIncident } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('authenticated API client', () => {
  it('posts credentials without authorization headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      accessToken: 'jwt', tokenType: 'Bearer', expiresAt: '2099-10-29T12:00:00Z',
      user: { id: 1, displayName: 'Operator', email: 'op@example.com', role: 'VIEWER' }
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const result = await login('op@example.com', 'a-password');
    expect(result.accessToken).toBe('jwt');
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/auth/login');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBeUndefined();
    expect(options.redirect).toBe('error');
    expect(options.credentials).toBe('omit');
    expect(JSON.parse(options.body)).toEqual({ email: 'op@example.com', password: 'a-password' });
  });

  it('rejects malformed login envelopes instead of storing an invalid JWT session', async () => {
    const valid = {
      accessToken: 'jwt', tokenType: 'Bearer', expiresAt: '2099-11-29T12:00:00Z',
      user: { id: 1, displayName: 'Operator', email: 'op@example.com', role: 'RESPONDER' }
    };
    const invalid = [
      { ...valid, accessToken: '' },
      { ...valid, tokenType: 'Basic' },
      { ...valid, expiresAt: 'not-a-date' },
      { ...valid, expiresAt: '2000-01-01T00:00:00Z' },
      { ...valid, expiresAt: new Date(Date.now() - 60_000).toISOString() },
      { ...valid, user: null },
      { ...valid, user: { ...valid.user, id: 0 } },
      { ...valid, user: { ...valid.user, role: 'SUPERUSER' } },
      { ...valid, user: { ...valid.user, email: '' } },
      [valid]
    ];
    const fetchMock = vi.fn();
    for (const payload of invalid) {
      fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(payload), { status: 200 }));
    }
    vi.stubGlobal('fetch', fetchMock);
    for (const _payload of invalid) {
      await expect(login('op@example.com', 'password')).rejects.toEqual(new ApiError(
        502, 'The server returned an invalid authentication response. Please retry or contact an administrator.'
      ));
    }
    expect(fetchMock).toHaveBeenCalledTimes(invalid.length);
  });

  it('rejects impossible calendar dates and accepts leap-day timezone offsets', async () => {
    const valid = {
      accessToken: 'jwt', tokenType: 'Bearer', expiresAt: '2096-02-29T23:45:00+05:30',
      user: { id: 1, displayName: 'Operator', email: 'op@example.com', role: 'VIEWER' }
    };
    const invalidDates = [
      '2099-02-29T12:00:00Z', '2099-04-31T12:00:00Z',
      '2099-13-01T12:00:00Z', '2099-01-01T24:00:00Z',
      '2099-01-01T12:60:00Z', '2099-01-01T12:00:00+24:00'
    ];
    const fetchMock = vi.fn();
    for (const expiresAt of invalidDates) {
      fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ ...valid, expiresAt }), { status: 200 }));
    }
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(valid), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    for (const _date of invalidDates) {
      await expect(login('op@example.com', 'password')).rejects.toEqual(new ApiError(
        502, 'The server returned an invalid authentication response. Please retry or contact an administrator.'
      ));
    }
    expect((await login('op@example.com', 'password')).expiresAt).toBe(valid.expiresAt);
    expect(fetchMock).toHaveBeenCalledTimes(invalidDates.length + 1);
  });

  it('fails closed on redirected API requests without retrying with credentials', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    vi.stubGlobal('fetch', fetchMock);
    await expect(getServices('secret')).rejects.toThrow('Failed to fetch');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      redirect: 'error', credentials: 'omit',
      headers: { Authorization: 'Bearer secret' }
    });
  });

  it('adds bearer authorization to service requests and forwards cancellation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await getServices('secret', controller.signal)).toEqual([]);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/services');
    expect(options.headers.Authorization).toBe('Bearer secret');
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal).not.toBe(controller.signal);
    controller.abort();
    expect(options.signal.aborted).toBe(true);
    expect(options.cache).toBe('no-store');
    expect(options.redirect).toBe('error');
    expect(options.credentials).toBe('omit');
  });

  it('does not send a request when the caller signal is already aborted', async () => {
    const controller = new AbortController();
    const reason = new DOMException('Operator signed out', 'AbortError');
    controller.abort(reason);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(getServices('secret', controller.signal)).rejects.toBe(reason);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does not dispatch requests when their deadline has already expired', async () => {
    const timeoutController = new AbortController();
    timeoutController.abort(new DOMException('Deadline', 'TimeoutError'));
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal);
    try {
      const fetchMock = vi.fn();
      vi.stubGlobal('fetch', fetchMock);
      await expect(getServices('secret')).rejects.toEqual(
        new ApiError(0, 'The request timed out. Please retry.')
      );
      expect(fetchMock).not.toHaveBeenCalled();
    } finally {
      timeoutSpy.mockRestore();
    }
  });

  it('ignores an HTTP auth error when the operator signs out before fetch resolves', async () => {
    const controller = new AbortController();
    const reason = new DOMException('Operator signed out', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => {
      controller.abort(reason);
      return new Response('private proxy diagnostic', { status: 401 });
    }));
    await expect(getServices('secret', controller.signal)).rejects.toBe(reason);
  });

  it('reports a deadline instead of a stale HTTP failure when fetch resolves late', async () => {
    const timeoutController = new AbortController();
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal);
    try {
      vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => {
        timeoutController.abort(new DOMException('Deadline', 'TimeoutError'));
        return new Response('upstream unavailable', { status: 503 });
      }));
      await expect(getServices('secret')).rejects.toEqual(
        new ApiError(0, 'The request timed out. Please retry.')
      );
    } finally {
      timeoutSpy.mockRestore();
    }
  });

  it('discards a parsed success response when sign-out occurs during parsing', async () => {
    const controller = new AbortController();
    const reason = new DOMException('Operator signed out', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => {
        controller.abort(reason);
        return [{ id: 1 }];
      }
    }));
    await expect(getServices('secret', controller.signal)).rejects.toBe(reason);
  });

  it('reports a deadline reached during successful response parsing', async () => {
    const timeoutController = new AbortController();
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal);
    try {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: async () => {
          timeoutController.abort(new DOMException('Deadline', 'TimeoutError'));
          return [{ id: 1 }];
        }
      }));
      await expect(getServices('secret')).rejects.toEqual(
        new ApiError(0, 'The request timed out. Please retry.')
      );
    } finally {
      timeoutSpy.mockRestore();
    }
  });

  it('bounds stalled API calls and propagates timeout without retrying', async () => {
    const timeoutController = new AbortController();
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal);
    try {
      const fetchMock = vi.fn().mockImplementation((_url: string, options: RequestInit) =>
        new Promise((_resolve, reject) => {
          options.signal?.addEventListener('abort', () =>
            reject(options.signal?.reason), { once: true });
        })
      );
      vi.stubGlobal('fetch', fetchMock);
      const pending = getServices('secret');
      expect(timeoutSpy).toHaveBeenCalledWith(20_000);
      timeoutController.abort(new DOMException('API request timed out', 'TimeoutError'));
      await expect(pending).rejects.toEqual(new ApiError(0, 'The request timed out. Please retry.'));
      expect(fetchMock).toHaveBeenCalledTimes(1);
    } finally {
      timeoutSpy.mockRestore();
    }
  });

  it('preserves caller cancellation when it races with the deadline', async () => {
    const controller = new AbortController();
    const timeoutController = new AbortController();
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal);
    try {
      const fetchMock = vi.fn().mockImplementation((_url: string, options: RequestInit) =>
        new Promise((_resolve, reject) => {
          options.signal?.addEventListener('abort', () =>
            reject(options.signal?.reason), { once: true });
        })
      );
      vi.stubGlobal('fetch', fetchMock);
      const pending = getServices('secret', controller.signal);
      controller.abort(new DOMException('Operator signed out', 'AbortError'));
      timeoutController.abort(new DOMException('Request timed out', 'TimeoutError'));
      await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    } finally {
      timeoutSpy.mockRestore();
    }
  });

  it('does not mislabel ordinary network failures as timeouts', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    vi.stubGlobal('fetch', fetchMock);
    await expect(getServices('secret')).rejects.toThrow('Failed to fetch');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('allows caller cancellation before the timeout without retrying', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation((_url: string, options: RequestInit) =>
      new Promise((_resolve, reject) => {
        options.signal?.addEventListener('abort', () =>
          reject(options.signal?.reason), { once: true });
      })
    );
    vi.stubGlobal('fetch', fetchMock);
    const pending = getServices('secret', controller.signal);
    controller.abort(new DOMException('Operator signed out', 'AbortError'));
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects malformed successful API responses with a safe error', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('<html>internal gateway hostname</html>', { status: 200 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))
      .mockResolvedValueOnce(new Response('null', { status: 200 }))
      .mockResolvedValueOnce(new Response('42', { status: 200 }))
      .mockResolvedValueOnce(new Response('"unexpected"', { status: 200 }))
      .mockResolvedValueOnce(new Response('false', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    for (let attempt = 0; attempt < 6; attempt++) {
      await expect(getServices('secret')).rejects.toEqual(new ApiError(
        502, 'The server returned an invalid response. Please retry or contact an administrator.'
      ));
    }
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it('preserves caller cancellation while parsing a successful response', async () => {
    const controller = new AbortController();
    const abortError = new DOMException('Operator signed out', 'AbortError');
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => new Promise((_resolve, reject) => {
        controller.signal.addEventListener('abort', () => reject(abortError), { once: true });
      })
    });
    vi.stubGlobal('fetch', fetchMock);
    const pending = getServices('secret', controller.signal);
    // Allow the fetch continuation to begin parsing before cancellation.
    await Promise.resolve();
    controller.abort(abortError);
    await expect(pending).rejects.toBe(abortError);
  });

  it('reads notification health with a bearer token, no cache, and cancellation', async () => {
    const snapshot = {
      sampledAt: '2026-10-31T00:00:00Z', pending: 2, sending: 1,
      sent: 5, failed: 0, oldestPendingAgeSeconds: 90
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(snapshot), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await getNotificationSummary('secret', controller.signal)).toEqual(snapshot);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/notifications/summary');
    expect(options.headers.Authorization).toBe('Bearer secret');
    expect(options.cache).toBe('no-store');
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal).not.toBe(controller.signal);
    controller.abort();
    expect(options.signal.aborted).toBe(true);
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
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal).not.toBe(controller.signal);
    controller.abort();
    expect(options.signal.aborted).toBe(true);
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

  it('bounds and normalizes untrusted validation messages without losing conflict status', async () => {
    const longMessage = 'x'.repeat(1200);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: '\u0000  Invalid\n status\t  ' }), { status: 400 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: longMessage }), { status: 409 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: ' \n\t ' }), { status: 422 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(getServices('token')).rejects.toEqual(new ApiError(400, 'Invalid  status'));
    await expect(getServices('token')).rejects.toEqual(new ApiError(409, 'x'.repeat(500)));
    await expect(getServices('token')).rejects.toEqual(new ApiError(422, 'Request failed (422)'));
  });

  it('redacts unexpected client-error bodies while retaining HTTP status', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Internal router path /admin/private and upstream secret'
      }), { status: 404 }))
      .mockResolvedValueOnce(new Response('<html>proxy backend=internal-host</html>', { status: 405 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Gateway policy secret=do-not-expose'
      }), { status: 408 }));
    vi.stubGlobal('fetch', fetchMock);
    for (const status of [404, 405, 408]) {
      await expect(getServices('token')).rejects.toEqual(
        new ApiError(status, 'Request failed (' + status + '). Please retry or contact an administrator.')
      );
    }
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('preserves bounded application validation details for 422', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Invalid incident field'
    }), { status: 422 })));
    await expect(getServices('token')).rejects.toEqual(
      new ApiError(422, 'Invalid incident field')
    );
  });

  it('reports JSON errors and non-JSON security responses', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Invalid credentials' }), { status: 401 }))
      .mockResolvedValueOnce(new Response('Forbidden', { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(login('op@example.com', 'wrong')).rejects.toMatchObject({
      status: 401, message: 'Authentication required. Please sign in again.'
    });
    await expect(getServices('token')).rejects.toEqual(new ApiError(403, 'You do not have permission to perform this action.'));
  });

  it('never exposes untrusted authentication and authorization error bodies', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Internal LDAP host=private.example password=secret'
      }), { status: 401 }))
      .mockResolvedValueOnce(new Response('<html>internal policy token=private</html>', { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(login('op@example.com', 'wrong')).rejects.toEqual(
      new ApiError(401, 'Authentication required. Please sign in again.')
    );
    await expect(getServices('token')).rejects.toEqual(
      new ApiError(403, 'You do not have permission to perform this action.')
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('redacts untrusted rate-limit responses while preserving 429 for retry handling', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Proxy policy internal-bucket=secret and private upstream details'
      }), { status: 429, headers: { 'Retry-After': '60' } }))
      .mockResolvedValueOnce(new Response('<html>internal proxy configuration</html>', { status: 429 }));
    vi.stubGlobal('fetch', fetchMock);
    for (let attempt = 0; attempt < 2; attempt++) {
      await expect(getServices('operator-token')).rejects.toEqual(
        new ApiError(429, 'Too many requests. Please wait before retrying.')
      );
    }
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('redacts sensitive server errors while retaining HTTP status for handling', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Database password secret=do-not-expose; SQL connection failed'
      }), { status: 500 }))
      .mockResolvedValueOnce(new Response('<html>upstream token=private</html>', { status: 502 }));
    vi.stubGlobal('fetch', fetchMock);

    for (const status of [500, 502]) {
      try {
        await getServices('operator-token');
        throw new Error('Expected a server error');
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({
          status,
          message: 'The server could not complete the request. Please retry or contact an administrator.'
        });
        expect(String(error)).not.toMatch(/password|secret|token=private|SQL connection/i);
      }
    }
  });

  it('loads incident history through bounded keyset pages with auth and cancellation', async () => {
    expect(incidentHistoryCursorQuery(19, null, 50))
      .toBe('/api/v1/incidents/19/history/cursor?size=50');
    expect(incidentHistoryCursorQuery(19, {
      afterCreatedAt: '2026-11-02T00:00:00Z', afterId: 42
    }, 50)).toBe('/api/v1/incidents/19/history/cursor?size=50&afterCreatedAt=2026-11-02T00%3A00%3A00Z&afterId=42');
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await getIncidentHistoryPage('secret', 19, null, 50, controller.signal)).toEqual([]);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/incidents/19/history/cursor?size=50');
    expect(options.headers.Authorization).toBe('Bearer secret');
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal).not.toBe(controller.signal);
    controller.abort();
    expect(options.signal.aborted).toBe(true);
    expect(options.cache).toBe('no-store');
  });
});


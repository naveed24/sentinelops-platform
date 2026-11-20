import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, login } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('login credential envelope hardening', () => {
  const valid = {
    accessToken: 'eyJhbGciOiJIUzI1NiJ9.payload.signature',
    tokenType: 'Bearer',
    expiresAt: '2026-12-01T12:00:00Z',
    user: { id: 1, displayName: 'Responder', email: 'op@example.com', role: 'RESPONDER' }
  };

  it.each([
    ['embedded space', { accessToken: 'token with space' }],
    ['newline', { accessToken: 'token\nAuthorization: Basic secret' }],
    ['leading whitespace', { accessToken: ' token' }],
    ['local expiry', { expiresAt: '2026-12-01T12:00:00' }],
    ['date-only expiry', { expiresAt: '2026-12-01' }],
    ['non-ISO expiry', { expiresAt: 'December 1, 2026' }]
  ])('rejects %s', async (_name, override) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ...valid, ...override }), { status: 200 })
    ));
    await expect(login('op@example.com', 'password')).rejects.toEqual(new ApiError(
      502, 'The server returned an invalid authentication response. Please retry or contact an administrator.'
    ));
  });

  it('accepts a timezone-offset expiry and a JWT-shaped bearer credential', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ...valid, expiresAt: '2026-12-01T17:30:00+05:30' }), { status: 200 })
    ));
    expect((await login('op@example.com', 'password')).accessToken).toBe(valid.accessToken);
  });
});

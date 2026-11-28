import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createIncident, type Operator, type ServiceRecord } from './api';
import { IncidentCreate } from './IncidentCreate';

afterEach(() => vi.unstubAllGlobals());

const service: ServiceRecord = {
  id: 7, name: 'orders-api', description: null, ownerTeam: 'commerce',
  status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z'
};

function view(role: Operator['role'], services: ServiceRecord[] = [service]) {
  return renderToStaticMarkup(createElement(IncidentCreate, {
    token: 'test-token', role, services, onCreated: vi.fn(), onUnauthorized: vi.fn()
  }));
}

describe('incident creation UI', () => {
  it('does not expose a creation form to VIEWER accounts', () => {
    expect(view('VIEWER')).not.toContain('Create incident');
    expect(view('VIEWER')).not.toContain('<form');
  });

  it('requires a title and existing service with backend-aligned input limits', () => {
    for (const role of ['ADMIN', 'RESPONDER'] as const) {
      const html = view(role);
      expect(html).toContain('Report new incident');
      expect(html).toContain('maxLength="180"');
      expect(html).toContain('maxLength="2000"');
      expect(html).toContain('Select service');
      expect(html).toContain('orders-api');
      expect(html).toContain('SEV1');
      expect(html).toContain('SEV4');
    }
    expect(view('ADMIN', [])).toContain('disabled=""');
  });
});

describe('incident creation API', () => {
  const draft = { title: 'Checkout failures', description: '5xx spike', severity: 'SEV2' as const, serviceId: 7 };

  it('POSTs the JWT-authorized incident request without cookies or redirects', async () => {
    const incident = {
      id: 59, ...draft, status: 'OPEN', version: 0,
      createdAt: '2026-11-28T00:00:00Z', updatedAt: '2026-11-28T00:00:00Z', resolvedAt: null
    };
    const mock = vi.fn().mockResolvedValue(new Response(JSON.stringify(incident), { status: 201 }));
    vi.stubGlobal('fetch', mock);
    await expect(createIncident('token', draft)).resolves.toEqual(incident);
    const [url, options] = mock.mock.calls[0];
    expect(url).toBe('/api/v1/incidents');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe('Bearer token');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(options.credentials).toBe('omit');
    expect(options.redirect).toBe('error');
    expect(JSON.parse(options.body)).toEqual(draft);
  });

  it('preserves actionable API validation errors and status codes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: 'Invalid service ID' }), { status: 400 })
    ));
    await expect(createIncident('token', draft)).rejects.toEqual(new ApiError(400, 'Invalid service ID'));
  });

  it('does not expose untrusted unauthorized response details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('internal authentication header', { status: 401 })
    ));
    await expect(createIncident('token', draft)).rejects.toEqual(new ApiError(
      401, 'Authentication required. Please sign in again.'
    ));
  });
});

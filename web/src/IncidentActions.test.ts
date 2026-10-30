import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { IncidentActions } from './IncidentActions';
import type { Incident, Operator } from './api';

const incident: Incident = {
  id: 42, title: 'High error rate', description: null, severity: 'SEV2',
  status: 'OPEN', serviceId: 3, version: 7,
  createdAt: '2026-10-30T00:00:00Z', updatedAt: '2026-10-30T00:00:00Z',
  resolvedAt: null
};

function render(role: Operator['role'], status: Incident['status'] = 'OPEN') {
  return renderToStaticMarkup(createElement(IncidentActions, {
    incident: { ...incident, status }, token: 'test-token', role,
    onUpdated: vi.fn(), onStale: vi.fn(), onUnauthorized: vi.fn()
  }));
}

describe('incident action visibility', () => {
  it('keeps viewer accounts read-only', () => {
    const html = render('VIEWER');
    expect(html).toContain('read-only access');
    expect(html).not.toContain('Update status');
  });

  it('shows only valid choices and requires an audit message', () => {
    const html = render('RESPONDER');
    expect(html).toContain('ACKNOWLEDGED');
    expect(html).toContain('MITIGATED');
    expect(html).not.toContain('value="RESOLVED"');
    expect(html).toContain('required=""');
    expect(html).toContain('maxLength="500"');
  });

  it('does not offer transitions for resolved incidents', () => {
    const html = render('ADMIN', 'RESOLVED');
    expect(html).toContain('No further transitions');
    expect(html).not.toContain('Update status');
  });
});

import { describe, expect, it } from 'vitest';
import { appendAuditPage, nextAuditCursor } from './historyCursor';
import type { AuditEvent } from './api';

function event(id: number, createdAt = '2026-11-02T00:00:00Z'): AuditEvent {
  return { id, createdAt, fromStatus: 'OPEN', toStatus: 'ACKNOWLEDGED',
    message: 'Acknowledged', actorUserId: 1, actorEmail: 'operator@example.com' };
}

describe('incident audit keyset paging', () => {
  it('uses the last event timestamp and ID, including tied timestamps', () => {
    expect(nextAuditCursor([])).toBeNull();
    expect(nextAuditCursor([event(1), event(2)])).toEqual({
      afterCreatedAt: '2026-11-02T00:00:00Z', afterId: 2
    });
  });

  it('appends new pages without duplicating overlapping IDs or reordering history', () => {
    expect(appendAuditPage([event(1), event(2)], [event(2), event(3), event(3), event(4)]).map((item) => item.id))
      .toEqual([1, 2, 3, 4]);
  });
});

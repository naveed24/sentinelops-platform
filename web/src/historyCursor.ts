import type { AuditCursor, AuditEvent } from './api';

/** Always advance using the last event's timestamp and id (timestamps can collide). */
export function nextAuditCursor(events: readonly AuditEvent[]): AuditCursor | null {
  const last = events[events.length - 1];
  return last ? { afterCreatedAt: last.createdAt, afterId: last.id } : null;
}

/** Defensive de-duplication if an operator retries or a page overlaps. */
export function appendAuditPage(previous: readonly AuditEvent[], page: readonly AuditEvent[]): AuditEvent[] {
  const seen = new Set(previous.map((event) => event.id));
  const result = [...previous];
  for (const event of page) {
    if (!seen.has(event.id)) {
      seen.add(event.id);
      result.push(event);
    }
  }
  return result;
}

package com.sentinelops.incident;

import com.sentinelops.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bounded keyset access to incident audit events. The cursor is the
 * (createdAt, id) tuple of the last event returned to the caller.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncidentHistoryCursorService {
    private final IncidentRepository incidents;
    private final IncidentAuditEventRepository audit;

    public List<IncidentDtos.AuditResponse> page(
            Long incidentId, Instant afterCreatedAt, Long afterId, int size) {
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        if ((afterCreatedAt == null) != (afterId == null)) {
            throw new IllegalArgumentException("afterCreatedAt and afterId must be provided together");
        }
        if (afterId != null && afterId < 1) {
            throw new IllegalArgumentException("afterId must be positive");
        }
        if (!incidents.existsById(incidentId)) {
            throw new NotFoundException("Incident not found: " + incidentId);
        }

        var pageable = PageRequest.of(0, size);
        var events = afterCreatedAt == null
                ? audit.findByIncidentIdOrderByCreatedAtAsc(incidentId, pageable)
                : audit.findAfterCursor(incidentId, afterCreatedAt, afterId, pageable);
        return events.stream().map(IncidentDtos.AuditResponse::from).toList();
    }
}

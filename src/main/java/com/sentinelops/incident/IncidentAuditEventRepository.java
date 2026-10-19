package com.sentinelops.incident;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IncidentAuditEventRepository extends JpaRepository<IncidentAuditEvent, Long> {
    // IDs break timestamp ties, so history is stable across reads and database plans.
    @Query("select e from IncidentAuditEvent e where e.incidentId = :incidentId " +
           "order by e.createdAt asc, e.id asc")
    List<IncidentAuditEvent> findByIncidentIdOrderByCreatedAtAsc(
            @Param("incidentId") Long incidentId, Pageable pageable);

    // Keyset pagination uses the same (createdAt, id) ordering as the timeline index.
    @Query("select e from IncidentAuditEvent e where e.incidentId = :incidentId " +
           "and (e.createdAt > :afterCreatedAt " +
           "or (e.createdAt = :afterCreatedAt and e.id > :afterId)) " +
           "order by e.createdAt asc, e.id asc")
    List<IncidentAuditEvent> findAfterCursor(
            @Param("incidentId") Long incidentId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable);
}

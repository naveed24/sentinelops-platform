package com.sentinelops.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidentAuditEventRepository extends JpaRepository<IncidentAuditEvent, Long> {
    // IDs break timestamp ties, so history is stable across reads and database plans.
    @Query("select e from IncidentAuditEvent e where e.incidentId = :incidentId " +
           "order by e.createdAt asc, e.id asc")
    List<IncidentAuditEvent> findByIncidentIdOrderByCreatedAtAsc(
            @Param("incidentId") Long incidentId);
}

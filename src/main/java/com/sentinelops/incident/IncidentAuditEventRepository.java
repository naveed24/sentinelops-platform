package com.sentinelops.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentAuditEventRepository extends JpaRepository<IncidentAuditEvent, Long> {
    List<IncidentAuditEvent> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}

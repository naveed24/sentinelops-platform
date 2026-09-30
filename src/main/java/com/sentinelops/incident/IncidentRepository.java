package com.sentinelops.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    List<Incident> findBySeverityOrderByCreatedAtDesc(IncidentSeverity severity);
    List<Incident> findByServiceIdOrderByCreatedAtDesc(Long serviceId);
}

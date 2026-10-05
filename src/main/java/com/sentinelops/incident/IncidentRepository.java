package com.sentinelops.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    List<Incident> findBySeverityOrderByCreatedAtDesc(IncidentSeverity severity);
    List<Incident> findByServiceIdOrderByCreatedAtDesc(Long serviceId);
}

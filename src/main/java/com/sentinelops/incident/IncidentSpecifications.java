package com.sentinelops.incident;

import org.springframework.data.jpa.domain.Specification;

public final class IncidentSpecifications {
    private IncidentSpecifications() {}

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasSeverity(IncidentSeverity severity) {
        return (root, query, cb) -> severity == null ? cb.conjunction() : cb.equal(root.get("severity"), severity);
    }

    public static Specification<Incident> belongsToService(Long serviceId) {
        return (root, query, cb) -> serviceId == null ? cb.conjunction() : cb.equal(root.get("service").get("id"), serviceId);
    }
}

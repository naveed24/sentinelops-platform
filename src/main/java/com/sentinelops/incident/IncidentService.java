package com.sentinelops.incident;

import com.sentinelops.common.NotFoundException;
import com.sentinelops.service.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncidentService {
    private final IncidentRepository incidents;
    private final IncidentAuditEventRepository audit;
    private final ServiceRepository services;

    @Transactional
    public IncidentDtos.IncidentResponse create(IncidentDtos.CreateIncidentRequest request) {
        var target = services.findById(request.serviceId())
                .orElseThrow(() -> new NotFoundException("Service not found: " + request.serviceId()));
        var incident = incidents.save(Incident.builder()
                .title(request.title().trim())
                .description(request.description())
                .severity(request.severity())
                .status(IncidentStatus.OPEN)
                .service(target)
                .build());
        audit.save(IncidentAuditEvent.builder()
                .incidentId(incident.getId())
                .toStatus(IncidentStatus.OPEN)
                .message("Incident created")
                .build());
        return IncidentDtos.IncidentResponse.from(incident);
    }

    public IncidentDtos.IncidentResponse get(Long id) {
        return IncidentDtos.IncidentResponse.from(find(id));
    }

    public List<IncidentDtos.IncidentResponse> list(
            IncidentStatus status,
            IncidentSeverity severity,
            Long serviceId) {
        var specification = IncidentSpecifications.hasStatus(status)
                .and(IncidentSpecifications.hasSeverity(severity))
                .and(IncidentSpecifications.belongsToService(serviceId));

        return incidents.findAll(specification).stream()
                .map(IncidentDtos.IncidentResponse::from)
                .toList();
    }

    public List<IncidentDtos.AuditResponse> history(Long id) {
        find(id);
        return audit.findByIncidentIdOrderByCreatedAtAsc(id).stream()
                .map(IncidentDtos.AuditResponse::from).toList();
    }

    @Transactional
    public IncidentDtos.IncidentResponse transition(Long id, IncidentDtos.TransitionRequest request) {
        var incident = find(id);
        var from = incident.getStatus();
        var to = request.status();
        if (!allowed(from, to)) {
            throw new IllegalArgumentException("Invalid incident transition: " + from + " -> " + to);
        }
        incident.setStatus(to);
        if (to == IncidentStatus.RESOLVED) incident.setResolvedAt(Instant.now());
        incident = incidents.save(incident);
        audit.save(IncidentAuditEvent.builder()
                .incidentId(incident.getId())
                .fromStatus(from)
                .toStatus(to)
                .message(request.message().trim())
                .build());
        return IncidentDtos.IncidentResponse.from(incident);
    }

    private boolean allowed(IncidentStatus from, IncidentStatus to) {
        return switch (from) {
            case OPEN -> to == IncidentStatus.ACKNOWLEDGED || to == IncidentStatus.MITIGATED;
            case ACKNOWLEDGED -> to == IncidentStatus.MITIGATED;
            case MITIGATED -> to == IncidentStatus.RESOLVED;
            case RESOLVED -> false;
        };
    }

    private Incident find(Long id) {
        return incidents.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + id));
    }
}

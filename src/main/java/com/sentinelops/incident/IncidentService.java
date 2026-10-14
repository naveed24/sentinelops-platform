package com.sentinelops.incident;

import com.sentinelops.common.NotFoundException;
import com.sentinelops.common.ConflictException;
import com.sentinelops.common.UnauthorizedException;
import com.sentinelops.identity.UserAccountRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sentinelops.service.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final UserAccountRepository users;

    @Transactional
    public IncidentDtos.IncidentResponse create(IncidentDtos.CreateIncidentRequest request) {
        var target = services.findById(request.serviceId()).orElseThrow(() -> new NotFoundException("Service not found: " + request.serviceId()));
        var incident = incidents.save(Incident.builder().title(request.title().trim()).description(request.description()).severity(request.severity()).status(IncidentStatus.OPEN).service(target).build());
        var actor = currentActor();
        audit.save(IncidentAuditEvent.builder().incidentId(incident.getId())
                .actorUserId(actor.userId()).actorEmail(actor.email())
                .toStatus(IncidentStatus.OPEN).message("Incident created").build());
        return IncidentDtos.IncidentResponse.from(incident);
    }

    public IncidentDtos.IncidentResponse get(Long id) { return IncidentDtos.IncidentResponse.from(find(id)); }

    public List<IncidentDtos.IncidentResponse> list(IncidentStatus status, IncidentSeverity severity, Long serviceId, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        var specification = IncidentSpecifications.hasStatus(status).and(IncidentSpecifications.hasSeverity(severity)).and(IncidentSpecifications.belongsToService(serviceId));
        var page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return incidents.findAll(specification, page).stream().map(IncidentDtos.IncidentResponse::from).toList();
    }

    public List<IncidentDtos.AuditResponse> history(Long id) {
        find(id);
        return audit.findByIncidentIdOrderByCreatedAtAsc(id).stream().map(IncidentDtos.AuditResponse::from).toList();
    }

    @Transactional
    public IncidentDtos.IncidentResponse transition(Long id, IncidentDtos.TransitionRequest request) {
        var incident = find(id); var from = incident.getStatus(); var to = request.status();
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(incident.getVersion())) {
            throw new ConflictException("Incident was modified; fetch the latest version and retry");
        }
        if (!allowed(from, to)) throw new IllegalArgumentException("Invalid incident transition: " + from + " -> " + to);
        incident.setStatus(to);
        if (to == IncidentStatus.RESOLVED) incident.setResolvedAt(Instant.now());
        // Flush optimistic-lock updates before writing an audit event.
        // A concurrent winner must not produce an audit record for this loser.
        incident = incidents.saveAndFlush(incident);
        var actor = currentActor();
        audit.save(IncidentAuditEvent.builder().incidentId(incident.getId())
                .actorUserId(actor.userId()).actorEmail(actor.email())
                .fromStatus(from).toStatus(to).message(request.message().trim()).build());
        return IncidentDtos.IncidentResponse.from(incident);
    }

    private AuditActor currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            // Direct service calls and historical system events have no human actor.
            return new AuditActor(null, null);
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .map(user -> new AuditActor(user.getId(), user.getEmail()))
                .orElseThrow(() -> new UnauthorizedException("Authenticated user is unavailable"));
    }

    private record AuditActor(Long userId, String email) {}

    private boolean allowed(IncidentStatus from, IncidentStatus to) {
        return switch (from) {
            case OPEN -> to == IncidentStatus.ACKNOWLEDGED || to == IncidentStatus.MITIGATED;
            case ACKNOWLEDGED -> to == IncidentStatus.MITIGATED;
            case MITIGATED -> to == IncidentStatus.RESOLVED;
            case RESOLVED -> false;
        };
    }

    private Incident find(Long id) { return incidents.findById(id).orElseThrow(() -> new NotFoundException("Incident not found: " + id)); }
}
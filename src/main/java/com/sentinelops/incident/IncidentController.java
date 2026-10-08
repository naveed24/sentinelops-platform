package com.sentinelops.incident;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDtos.IncidentResponse create(@Valid @RequestBody IncidentDtos.CreateIncidentRequest request) {
        return incidentService.create(request);
    }

    @GetMapping
    public List<IncidentDtos.IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int page) {
        return incidentService.list(status, severity, serviceId, limit, page);
    }

    @GetMapping("/{id}")
    public IncidentDtos.IncidentResponse get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @GetMapping("/{id}/history")
    public List<IncidentDtos.AuditResponse> history(@PathVariable Long id) {
        return incidentService.history(id);
    }

    @PatchMapping("/{id}/status")
    public IncidentDtos.IncidentResponse transition(
            @PathVariable Long id, @Valid @RequestBody IncidentDtos.TransitionRequest request) {
        return incidentService.transition(id, request);
    }
}
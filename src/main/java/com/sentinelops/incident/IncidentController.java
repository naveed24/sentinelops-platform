package com.sentinelops.incident;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;
    private final IncidentHistoryCursorService cursorHistory;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit) {
        return incidentService.list(status, severity, serviceId, page, limit);
    }

    @GetMapping("/{id}")
    public IncidentDtos.IncidentResponse get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @GetMapping("/{id}/history")
    public List<IncidentDtos.AuditResponse> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return incidentService.history(id, page, size);
    }

    @GetMapping("/{id}/history/cursor")
    public List<IncidentDtos.AuditResponse> historyCursor(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterCreatedAt,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "100") int size) {
        return cursorHistory.page(id, afterCreatedAt, afterId, size);
    }

    @PatchMapping("/{id}/status")
    public IncidentDtos.IncidentResponse transition(
            @PathVariable Long id, @Valid @RequestBody IncidentDtos.TransitionRequest request) {
        return incidentService.transition(id, request);
    }
}
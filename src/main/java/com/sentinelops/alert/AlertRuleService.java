package com.sentinelops.alert;

import com.sentinelops.common.ConflictException;
import com.sentinelops.common.NotFoundException;
import com.sentinelops.service.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRuleService {
    private final AlertRuleRepository rules;
    private final ServiceRepository services;

    @Transactional
    public AlertRuleDtos.Response create(AlertRuleDtos.CreateRequest request) {
        var service = services.findById(request.serviceId())
                .orElseThrow(() -> new NotFoundException("Service not found: " + request.serviceId()));
        String name = request.name().trim();
        String canonical = canonical(name);
        if (rules.existsByService_IdAndCanonicalName(service.getId(), canonical)) {
            throw duplicate(name);
        }
        var rule = AlertRule.builder()
                .service(service).name(name).canonicalName(canonical)
                .eventType(request.eventType()).severity(request.severity())
                .enabled(request.enabled()).cooldownSeconds(request.cooldownSeconds())
                .build();
        try {
            // Flush so the database uniqueness guard catches concurrent creators here.
            return AlertRuleDtos.Response.from(rules.saveAndFlush(rule));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(name);
        }
    }

    public AlertRuleDtos.Response get(Long id) {
        return AlertRuleDtos.Response.from(find(id));
    }

    public List<AlertRuleDtos.Response> list(Long serviceId, int page, int size) {
        if (serviceId != null && serviceId <= 0) {
            throw new IllegalArgumentException("serviceId must be positive");
        }
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page must be non-negative and size must be 1..100");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var result = serviceId == null ? rules.findAll(pageable)
                : rules.findByService_Id(serviceId, pageable);
        return result.stream().map(AlertRuleDtos.Response::from).toList();
    }

    @Transactional
    public AlertRuleDtos.Response update(Long id, AlertRuleDtos.UpdateRequest request) {
        AlertRule rule = find(id);
        if (!request.expectedVersion().equals(rule.getVersion())) {
            throw new ConflictException("Alert rule was modified; fetch the latest version and retry");
        }
        String name = request.name().trim();
        String canonical = canonical(name);
        if (rules.existsByService_IdAndCanonicalNameAndIdNot(
                rule.getService().getId(), canonical, id)) {
            throw duplicate(name);
        }
        rule.setName(name);
        rule.setCanonicalName(canonical);
        rule.setEventType(request.eventType());
        rule.setSeverity(request.severity());
        rule.setEnabled(request.enabled());
        rule.setCooldownSeconds(request.cooldownSeconds());
        try {
            // @Version protects concurrent updates; the unique key protects renames.
            return AlertRuleDtos.Response.from(rules.saveAndFlush(rule));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(name);
        }
    }

    private AlertRule find(Long id) {
        return rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert rule not found: " + id));
    }

    private static String canonical(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("Alert rule name already exists for service: " + name);
    }
}

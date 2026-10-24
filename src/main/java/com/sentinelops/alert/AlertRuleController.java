package com.sentinelops.alert;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {
    private final AlertRuleService alertRules;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleDtos.Response create(@Valid @RequestBody AlertRuleDtos.CreateRequest request) {
        return alertRules.create(request);
    }

    @GetMapping("/{id}")
    public AlertRuleDtos.Response get(@PathVariable Long id) {
        return alertRules.get(id);
    }

    @GetMapping
    public List<AlertRuleDtos.Response> list(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return alertRules.list(serviceId, page, size);
    }

    @PutMapping("/{id}")
    public AlertRuleDtos.Response update(
            @PathVariable Long id, @Valid @RequestBody AlertRuleDtos.UpdateRequest request) {
        return alertRules.update(id, request);
    }
}

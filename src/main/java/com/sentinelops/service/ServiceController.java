package com.sentinelops.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRegistryService serviceRegistry;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDtos.ServiceResponse create(@Valid @RequestBody ServiceDtos.CreateServiceRequest request) {
        return serviceRegistry.create(request);
    }

    @GetMapping
    public List<ServiceDtos.ServiceResponse> list() {
        return serviceRegistry.list();
    }

    @GetMapping("/{id}")
    public ServiceDtos.ServiceResponse get(@PathVariable Long id) {
        return serviceRegistry.get(id);
    }

    @PatchMapping("/{id}/status")
    public ServiceDtos.ServiceResponse updateStatus(
            @PathVariable Long id,
            @RequestBody ServiceDtos.UpdateStatusRequest request) {
        return serviceRegistry.updateStatus(id, request);
    }
}

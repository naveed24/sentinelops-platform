package com.sentinelops.service;

import com.sentinelops.common.ConflictException;
import com.sentinelops.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceRegistryService {

    private final ServiceRepository repository;

    @Transactional
    public ServiceDtos.ServiceResponse create(ServiceDtos.CreateServiceRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Service name already exists: " + request.name());
        }
        ServiceRecord saved = repository.save(ServiceRecord.builder()
                .name(request.name().trim())
                .description(request.description())
                .ownerTeam(request.ownerTeam().trim())
                .status(ServiceStatus.ACTIVE)
                .build());
        return ServiceDtos.ServiceResponse.from(saved);
    }

    public List<ServiceDtos.ServiceResponse> list() {
        return repository.findAll().stream().map(ServiceDtos.ServiceResponse::from).toList();
    }

    public ServiceDtos.ServiceResponse get(Long id) {
        return ServiceDtos.ServiceResponse.from(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Service not found: " + id)));
    }

    @Transactional
    public ServiceDtos.ServiceResponse updateStatus(Long id, ServiceDtos.UpdateStatusRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("status is required");
        ServiceRecord service = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Service not found: " + id));
        service.setStatus(request.status());
        return ServiceDtos.ServiceResponse.from(service);
    }
}

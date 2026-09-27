package com.sentinelops.service;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<ServiceRecord, Long> {
    Optional<ServiceRecord> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}

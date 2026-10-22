package com.sentinelops.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalEventRepository extends JpaRepository<OperationalEvent, String> {
}

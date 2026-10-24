package com.sentinelops.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    boolean existsByService_IdAndCanonicalName(Long serviceId, String canonicalName);
    boolean existsByService_IdAndCanonicalNameAndIdNot(Long serviceId, String canonicalName, Long id);
    Page<AlertRule> findByService_Id(Long serviceId, Pageable pageable);
}

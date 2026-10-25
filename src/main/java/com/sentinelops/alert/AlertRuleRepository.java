package com.sentinelops.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    // Lock rules in a stable order so concurrent ingestion serializes cooldown decisions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AlertRule r where r.service.id = :serviceId and r.enabled = true " +
            "and r.eventType = :eventType order by r.id asc")
    List<AlertRule> lockEnabledMatchingRules(@Param("serviceId") Long serviceId,
                                              @Param("eventType") String eventType);
    boolean existsByService_IdAndCanonicalName(Long serviceId, String canonicalName);
    boolean existsByService_IdAndCanonicalNameAndIdNot(Long serviceId, String canonicalName, Long id);
    Page<AlertRule> findByService_Id(Long serviceId, Pageable pageable);
}

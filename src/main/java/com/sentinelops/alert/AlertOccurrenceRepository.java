package com.sentinelops.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertOccurrenceRepository extends JpaRepository<AlertOccurrence, Long> {
    long countByEvent_EventId(String eventId);
    boolean existsByRule_IdAndEvent_EventId(Long ruleId, String eventId);
}

package com.sentinelops.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AlertNotificationOutboxRepository extends JpaRepository<AlertNotificationOutbox, Long> {
    Optional<AlertNotificationOutbox> findFirstByRule_IdOrderByCreatedAtDescIdDesc(Long ruleId);
    long countByRule_Id(Long ruleId);
    boolean existsByOccurrence_Id(Long occurrenceId);
}

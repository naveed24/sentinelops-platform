package com.sentinelops.alert;

import com.sentinelops.ingestion.OperationalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates exact service/type matches for an event in the ingestion transaction.
 * Persisted occurrences are snapshots of severity at matching time.
 * Cooldown/delivery are deliberately not applied here: a later delivery
 * worker may coalesce occurrences without erasing the audit trail.
 */
@Service
@RequiredArgsConstructor
public class AlertOccurrenceEvaluator {
    private final AlertRuleRepository rules;
    private final AlertOccurrenceRepository occurrences;

    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(OperationalEvent event) {
        for (AlertRule rule : rules.findByService_IdAndEnabledTrueAndEventType(
                event.getService().getId(), event.getEventType())) {
            occurrences.save(AlertOccurrence.builder()
                    .rule(rule)
                    .event(event)
                    .severity(rule.getSeverity())
                    .build());
        }
    }
}

package com.sentinelops.alert;

import com.sentinelops.ingestion.OperationalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Writes immutable alert occurrences and cooldown-aware delivery intents in
 * the same transaction as the Kafka inbox. A rule row lock serializes
 * competing events for that rule, preventing duplicate cooldown notifications.
 */
@Service
@RequiredArgsConstructor
public class AlertOccurrenceEvaluator {
    private final AlertRuleRepository rules;
    private final AlertOccurrenceRepository occurrences;
    private final AlertNotificationOutboxRepository notifications;

    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(OperationalEvent event) {
        // Cooldown uses processing time, not potentially delayed producer timestamps.
        var matchingRules = rules.lockEnabledMatchingRules(
                event.getService().getId(), event.getEventType());
        // Capture time only after obtaining row locks; a waiting consumer must
        // not compare a stale pre-lock timestamp to a newer queued intent.
        Instant now = Instant.now();
        for (AlertRule rule : matchingRules) {
            // Always retain every match for audit, even when delivery is suppressed.
            AlertOccurrence occurrence = occurrences.save(AlertOccurrence.builder()
                    .rule(rule)
                    .event(event)
                    .severity(rule.getSeverity())
                    .build());

            var last = notifications.findFirstByRule_IdOrderByCreatedAtDescIdDesc(rule.getId());
            if (last.isPresent() && now.isBefore(
                    last.get().getCreatedAt().plusSeconds(rule.getCooldownSeconds()))) {
                continue;
            }

            notifications.save(AlertNotificationOutbox.builder()
                    .rule(rule)
                    .occurrence(occurrence)
                    .status(AlertNotificationStatus.PENDING)
                    .attemptCount(0)
                    .createdAt(now)
                    .nextAttemptAt(now)
                    .build());
        }
    }
}

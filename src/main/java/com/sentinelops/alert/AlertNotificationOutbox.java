package com.sentinelops.alert;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * One delivery intent for a qualifying occurrence. A database uniqueness
 * constraint prevents a single occurrence being scheduled twice.
 */
@Entity
@Table(name = "alert_notification_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uq_alert_notification_outbox_occurrence",
                columnNames = "occurrence_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertNotificationOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private AlertRule rule;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occurrence_id", nullable = false, updatable = false)
    private AlertOccurrence occurrence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertNotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

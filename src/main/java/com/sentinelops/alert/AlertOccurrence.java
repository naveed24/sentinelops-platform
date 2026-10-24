package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.ingestion.OperationalEvent;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Immutable record of one alert rule matching one accepted operational event. */
@Entity
@Table(name = "alert_occurrences", uniqueConstraints =
        @UniqueConstraint(name = "uq_alert_occurrences_rule_event", columnNames = {"rule_id", "event_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertOccurrence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private AlertRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private OperationalEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private IncidentSeverity severity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

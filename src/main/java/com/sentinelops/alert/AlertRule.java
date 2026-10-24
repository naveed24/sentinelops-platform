package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.service.ServiceRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alert_rules", uniqueConstraints = @UniqueConstraint(
        name = "uq_alert_rules_service_canonical_name",
        columnNames = {"service_id", "canonical_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceRecord service;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "canonical_name", nullable = false, length = 120)
    private String canonicalName;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

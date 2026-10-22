package com.sentinelops.ingestion;

import com.sentinelops.service.ServiceRecord;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "operational_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperationalEvent {
    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceRecord service;

    @Column(nullable = false, length = 120, updatable = false)
    private String source;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void setReceivedAt() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}

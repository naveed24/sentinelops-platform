package com.sentinelops.incident;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IncidentLifecycleMetrics {
    private final MeterRegistry registry;

    // Count only committed changes. Failed transitions and rollbacks do not increment.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordCommittedLifecycle(IncidentLifecycleEvent event) {
        Counter.builder("sentinelops.incident.lifecycle")
                .description("Committed incident creations and lifecycle transitions")
                .tag("operation", event.from() == null ? "created" : "transition")
                .tag("severity", event.severity().name())
                .tag("from", event.from() == null ? "NONE" : event.from().name())
                .tag("to", event.to().name())
                .register(registry)
                .increment();
    }
}

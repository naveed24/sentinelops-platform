package com.sentinelops.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Active only in deployments explicitly enabling the ingestion profile. */
@Component
@Profile("ingestion")
public class OperationalEventConsumer {
    private final OperationalEventIngestionService ingestion;

    public OperationalEventConsumer(OperationalEventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @KafkaListener(topics = "sentinelops.operational-events",
            groupId = "sentinelops-operational-events-v1")
    public void consume(String payload) {
        ingestion.ingest(payload);
    }
}

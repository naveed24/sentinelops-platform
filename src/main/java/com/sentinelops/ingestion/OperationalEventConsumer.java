package com.sentinelops.ingestion;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OperationalEventConsumer {
    private final OperationalEventIngestionService ingestion;

    public OperationalEventConsumer(OperationalEventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @KafkaListener(topics = "sentinelops.operational-events", autoStartup = "${sentinelops.ingestion.enabled:false}")
    public void consume(String payload) {
        ingestion.ingest(payload);
    }
}

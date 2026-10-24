package com.sentinelops.ingestion;

import com.sentinelops.service.ServiceRepository;
import com.sentinelops.alert.AlertOccurrenceEvaluator;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Commits the durable inbox row before the Kafka listener acknowledges the record.
 * A replay with the same eventId is a no-op. The database PK also prevents
 * concurrent deliveries from persisting two rows.
 */
@Service
@RequiredArgsConstructor
public class OperationalEventIngestionService {
    private static final int MAX_PAYLOAD_LENGTH = 16_384;
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final OperationalEventRepository events;
    private final ServiceRepository services;
    private final AlertOccurrenceEvaluator alertEvaluator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional
    public boolean ingest(String payload) {
        OperationalEventMessage message = parse(payload);
        String eventId = message.eventId().toString();
        if (events.existsById(eventId)) {
            return false;
        }

        var service = services.findById(message.serviceId()).orElseThrow(() ->
                new InvalidOperationalEventException("Unknown serviceId: " + message.serviceId()));

        // A unique event_id PK is the final guard against concurrent redelivery.
        // If a concurrent insert wins, the Kafka error handler retries and the
        // next attempt observes the committed event as a duplicate.
        var stored = events.saveAndFlush(OperationalEvent.builder()
                .eventId(eventId)
                .service(service)
                .source(message.source())
                .eventType(message.type())
                .message(message.message())
                .occurredAt(message.occurredAt())
                .build());
        // Match rules within the same transaction as inbox persistence.
        // A listener retry cannot commit an event without its alert occurrences.
        alertEvaluator.evaluate(stored);
        return true;
    }

    private OperationalEventMessage parse(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new InvalidOperationalEventException("Event payload is empty or exceeds 16384 characters");
        }
        final OperationalEventMessage message;
        try {
            message = objectMapper.readValue(payload, OperationalEventMessage.class);
        } catch (Exception ex) {
            throw new InvalidOperationalEventException("Malformed operational event JSON", ex);
        }
        if (message == null || message.schemaVersion() == null
                || message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new InvalidOperationalEventException("Unsupported operational event schema version");
        }
        if (!validator.validate(message).isEmpty()) {
            throw new InvalidOperationalEventException("Invalid operational event fields");
        }
        return message;
    }
}

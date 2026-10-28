package com.sentinelops.ingestion;

import com.sentinelops.service.ServiceRepository;
import com.sentinelops.alert.AlertOccurrenceEvaluator;
import jakarta.validation.Validator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
    private final ObservationRegistry observations;

    @Transactional
    public boolean ingest(String payload) {
        var observation = Observation.createNotStarted("sentinelops.ingestion.event", observations)
                .contextualName("ingest operational event")
                .start();
        try (var scope = observation.openScope()) {
            OperationalEventMessage message = parse(payload);
            observation.highCardinalityKeyValue("event.id", message.eventId().toString());
            observation.highCardinalityKeyValue("service.id", message.serviceId().toString());
            String eventId = message.eventId().toString();
            if (events.existsById(eventId)) {
                observation.lowCardinalityKeyValue("outcome", "duplicate");
                return false;
            }

            var service = services.findById(message.serviceId()).orElseThrow(() ->
                    new InvalidOperationalEventException("Unknown serviceId: " + message.serviceId()));

            // The event inbox and all matching alert occurrences commit together.
            // A concurrent duplicate insert rolls back and is retried by Kafka.
            var stored = events.saveAndFlush(OperationalEvent.builder()
                    .eventId(eventId)
                    .service(service)
                    .source(message.source())
                    .eventType(message.type())
                    .message(message.message())
                    .occurredAt(message.occurredAt())
                    .build());
            alertEvaluator.evaluate(stored);
            observation.lowCardinalityKeyValue("outcome", "accepted");
            return true;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue("outcome",
                    ex instanceof InvalidOperationalEventException ? "rejected" : "error");
            // Do not attach exception messages: parser errors may echo raw event data.
            observation.error(new IllegalStateException("operational_event_processing_failed"));
            throw ex;
        } finally {
            observation.stop();
        }
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

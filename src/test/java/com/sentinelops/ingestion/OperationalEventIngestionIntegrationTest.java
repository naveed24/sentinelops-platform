package com.sentinelops.ingestion;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OperationalEventIngestionIntegrationTest {
    @Autowired OperationalEventIngestionService ingestion;
    @Autowired OperationalEventRepository events;
    @Autowired ServiceRepository services;

    @Test
    void persistsEventAndIgnoresIdempotentRedelivery() {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("ingestion-payments").ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        UUID id = UUID.randomUUID();
        String payload = validPayload(id, service.getId());

        assertThat(ingestion.ingest(payload)).isTrue();
        assertThat(ingestion.ingest(payload)).isFalse();
        var stored = events.findById(id.toString()).orElseThrow();
        assertThat(stored.getEventType()).isEqualTo("ERROR_RATE");
        assertThat(stored.getService().getId()).isEqualTo(service.getId());
        assertThat(stored.getReceivedAt()).isNotNull();
    }

    @Test
    void listenerCommitsBeforeReturning() {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("ingestion-checkout").ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        UUID id = UUID.randomUUID();
        new OperationalEventConsumer(ingestion).consume(validPayload(id, service.getId()));
        assertThat(events.existsById(id.toString())).isTrue();
    }

    @Test
    void invalidSchemaAndUnknownServicesCannotEnterInbox() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> ingestion.ingest(validPayload(id, 9_999_999L)))
                .isInstanceOf(InvalidOperationalEventException.class)
                .hasMessageContaining("Unknown serviceId");
        assertThatThrownBy(() -> ingestion.ingest(validPayload(id, 9_999_999L)
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isInstanceOf(InvalidOperationalEventException.class)
                .hasMessageContaining("schema version");
        assertThatThrownBy(() -> ingestion.ingest("{\"schemaVersion\":1,\"eventId\":\"bad\"}"))
                .isInstanceOf(InvalidOperationalEventException.class);
        assertThat(events.existsById(id.toString())).isFalse();
    }

    private static String validPayload(UUID id, long serviceId) {
        return """
                {
                  "schemaVersion":1,
                  "eventId":"%s",
                  "serviceId":%d,
                  "source":"prometheus",
                  "type":"ERROR_RATE",
                  "message":"5xx ratio above threshold",
                  "occurredAt":"2026-10-22T00:00:00Z"
                }
                """.formatted(id, serviceId);
    }
}

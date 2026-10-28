package com.sentinelops.ingestion;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OperationalEventObservationIntegrationTest {
    @Autowired OperationalEventIngestionService ingestion;
    @Autowired ServiceRepository services;
    @Autowired ObservationRegistry registry;

    @Test
    void acceptedDuplicateAndRejectedOutcomesAreRecordedWithoutPayloadTags() {
        var seen = new ArrayList<Observation.Context>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context context) {
                return "sentinelops.ingestion.event".equals(context.getName());
            }
            @Override public void onStop(Observation.Context context) { seen.add(context); }
        });
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("tracing-" + UUID.randomUUID()).ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        UUID id = UUID.randomUUID();
        String json = """
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"ERROR_RATE",
                 "message":"private application message",
                 "occurredAt":"2026-10-28T00:00:00Z"}
                """.formatted(id, service.getId());

        assertThat(ingestion.ingest(json)).isTrue();
        assertThat(ingestion.ingest(json)).isFalse();
        assertThatThrownBy(() -> ingestion.ingest("{not-json"))
                .isInstanceOf(InvalidOperationalEventException.class);
        assertThat(seen).hasSize(3);
        assertThat(seen.stream().map(c -> c.getLowCardinalityKeyValues().toString()))
                .anySatisfy(s -> assertThat(s).contains("accepted"))
                .anySatisfy(s -> assertThat(s).contains("duplicate"))
                .anySatisfy(s -> assertThat(s).contains("rejected"));
        assertThat(seen.getFirst().getHighCardinalityKeyValues().toString())
                .contains(id.toString());
        for (var context : seen) {
            assertThat(context.getLowCardinalityKeyValues().toString())
                    .doesNotContain("private application message", id.toString());
            assertThat(context.getHighCardinalityKeyValues().toString())
                    .doesNotContain("private application message");
        }
    }
}

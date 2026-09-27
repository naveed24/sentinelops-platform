package com.sentinelops.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ServiceRegistryServiceTest {

    @Autowired
    ServiceRegistryService serviceRegistry;

    @Test
    void createsAndReadsService() {
        var created = serviceRegistry.create(new ServiceDtos.CreateServiceRequest(
                "orders-api", "Order lifecycle", "commerce"));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ServiceStatus.ACTIVE);
        assertThat(serviceRegistry.get(created.id()).name()).isEqualTo("orders-api");
    }
}

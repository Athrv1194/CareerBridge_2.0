package com.careerbridge.organization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL and the careerbridge_organization database to exist, same as every
 * other service's context test. RabbitMQ is NOT required: this service only publishes, and
 * RabbitTemplate connects lazily on first send rather than at startup.
 */
@SpringBootTest
class OrganizationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

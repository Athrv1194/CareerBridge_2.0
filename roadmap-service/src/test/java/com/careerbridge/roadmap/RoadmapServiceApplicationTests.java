package com.careerbridge.roadmap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL and the careerbridge_roadmap database to exist, same as every other
 * service's context test. RabbitMQ IS effectively required here, unlike organization-service: this
 * service declares a @RabbitListener queue, and the listener container attempts to connect and
 * declare it during context startup, not lazily on first send.
 */
@SpringBootTest
class RoadmapServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

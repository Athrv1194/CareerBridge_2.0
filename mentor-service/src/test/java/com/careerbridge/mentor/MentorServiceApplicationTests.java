package com.careerbridge.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The only test here that builds a real Spring context, and therefore the only one that can catch a
 * bean-wiring fault. Every other test in this service wires @InjectMocks directly and would stay
 * green through a BeanDefinitionOverrideException that stops the application starting at all --
 * exactly what happened to recruiter-service on 2026-08-01, when two RestClient @Bean methods were
 * named after the component-scanned classes they fed.
 *
 * Needs a reachable PostgreSQL (careerbridge_mentor) via the gitignored application-local.yml.
 * RabbitMQ may be down: this service declares no queue and no listener, so nothing on the
 * context-refresh path opens a broker connection.
 */
@SpringBootTest
class MentorServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

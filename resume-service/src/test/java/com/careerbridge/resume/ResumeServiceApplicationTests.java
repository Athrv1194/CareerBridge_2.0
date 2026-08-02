package com.careerbridge.resume;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL and the careerbridge_resume database to exist, same as every other
 * service's context test.
 *
 * Does NOT need RabbitMQ or student-service. This service declares no queue and no listener
 * (publisher-only), so there is nothing for AMQP to fail on at startup, and RestClient connects
 * lazily on first call -- so a green build here proves neither that the exchange exists nor that
 * student-service is reachable. Both are verified against the live stack instead.
 *
 * What it does prove, and the reason it is not a formality: that every bean wires. recruiter-service
 * shipped a RestClient @Bean named after its component-scanned client class and could not start at
 * all, while 84 unit tests stayed green -- contextLoads was the only thing that caught it.
 */
@SpringBootTest
class ResumeServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

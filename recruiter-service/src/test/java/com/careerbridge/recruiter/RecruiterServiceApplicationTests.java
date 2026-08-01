package com.careerbridge.recruiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL and the careerbridge_recruiter database to exist, same as every
 * other service's context test.
 *
 * Does NOT need RabbitMQ, student-service or prs-service. This service declares no queue and no
 * listener, so there is nothing for AMQP to fail on at startup, and both RestClients connect
 * lazily on first call -- so a green build here proves neither that the exchange exists nor that
 * either downstream service is reachable. Both are verified against the live stack instead.
 */
@SpringBootTest
class RecruiterServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

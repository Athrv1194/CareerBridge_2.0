package com.careerbridge.prs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL and the careerbridge_prs database to exist, same as every other
 * service's context test.
 *
 * Does NOT need RabbitMQ or student-service. The three listener containers log
 * "Failed to check/redeclare auto-delete queue(s)" and retry in the background rather than failing
 * startup, and RestClient connects lazily on first call -- so a green build here proves neither
 * that the queues exist nor that student-service is reachable. Both are verified against the live
 * stack instead.
 */
@SpringBootTest
class PrsServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

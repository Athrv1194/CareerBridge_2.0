package com.careerbridge.aicoach;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This is the one contextLoads in the repo that is genuinely load-bearing as a claim, not just a
 * formality: it must pass with no MongoDB, no Postgres, and no RabbitMQ reachable at all. No socket
 * opens during context refresh for a plain mongodb:// (non-+srv) URI -- verified against
 * MongoAutoConfiguration / MongoDatabaseFactoryConfiguration / MongoRepositoryFactory sources.
 */
@SpringBootTest
class AiCoachServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

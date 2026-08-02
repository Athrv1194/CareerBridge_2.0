package com.careerbridge.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a reachable PostgreSQL (ddl-auto builds the schema at context refresh), same as every other
 * JPA-backed service in this repo. Unlike ai-coach-service, this one is NOT green with nothing
 * running -- it has a real datasource.
 *
 * It is not a formality either: recruiter-service shipped a RestClient @Bean named after its own
 * component-scanned client class and could not start at all while 84 unit tests stayed green.
 * contextLoads was the only thing that caught it, which is why the Razorpay bean here is called
 * razorpayRestClient rather than razorpayClient.
 */
@SpringBootTest
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

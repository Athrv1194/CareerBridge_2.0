package com.careerbridge.roadmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Personalised learning roadmaps for CareerBridge. Consumes recommendation.generated, matches the
 * student's top career against a seeded template, and materialises the milestone checklist they
 * work through. Publishes roadmap.updated on every completion.
 *
 * No Spring Security on the classpath, by design, matching student/assessment/recommendation/
 * notification/organization. The caller's identity arrives as X-User-Id and X-User-Role, injected by
 * api-gateway after it validates the JWT; this service never parses a token.
 *
 * Note there is no UserDetailsServiceAutoConfiguration exclude here. auth-service needs one because
 * it has the security starter and Boot would otherwise log a generated password; with no security on
 * this classpath the class is absent, and naming an absent class in spring.autoconfigure.exclude
 * fails startup outright.
 *
 * Consequence of trusting those headers: port 8088 must never be publicly reachable, or anyone can
 * send X-User-Id and complete another student's milestones.
 */
@SpringBootApplication
public class RoadmapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoadmapServiceApplication.class, args);
    }
}

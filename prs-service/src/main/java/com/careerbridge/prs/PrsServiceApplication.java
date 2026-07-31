package com.careerbridge.prs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Placement Readiness Score engine. Computes a weighted composite telling a student how ready they
 * are for campus placement, from three signals the rest of the system already produces:
 *
 *   totalScore = assessmentScore x 0.40   (matchPercentage, recommendation.generated)
 *              + roadmapScore    x 0.30   (completionPercentage, roadmap.updated)
 *              + profileScore    x 0.20   (profileCompletionPercentage, sync GET from student-service)
 *              + 0.00            x 0.10   (reserved for the future resume builder)
 *
 * The reserved 10% is deliberate and visible: totalScore therefore tops out at 90, and PrsBreakdown
 * reports reservedWeight/reservedContribution so the missing ten points explain themselves rather
 * than looking like a rounding fault.
 *
 * This is the FIRST service in CareerBridge that makes a synchronous call to another service --
 * everything else communicates only through RabbitMQ. That call is fail-soft with explicit
 * timeouts; see StudentServiceClient for why both halves of that matter.
 *
 * No Spring Security on the classpath, by design, matching every other backend service. The
 * caller's identity arrives as X-User-Id, X-User-Role and X-User-Org-Id, injected by api-gateway
 * after it validates the JWT; this service never parses a token.
 *
 * Note there is no UserDetailsServiceAutoConfiguration exclude here. auth-service needs one because
 * it has the security starter; with no security on this classpath the class is absent, and naming
 * an absent class in spring.autoconfigure.exclude fails startup outright.
 *
 * Consequence of trusting those headers: port 8089 must never be publicly reachable, or anyone can
 * send X-User-Role: SUPER_ADMIN and read every student's score.
 */
@SpringBootApplication
public class PrsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrsServiceApplication.class, args);
    }
}

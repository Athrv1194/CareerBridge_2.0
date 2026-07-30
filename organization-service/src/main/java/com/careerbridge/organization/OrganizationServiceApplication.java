package com.careerbridge.organization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Multi-tenancy for CareerBridge: every college is an Organization, each owning Departments.
 *
 * No Spring Security on the classpath, by design, matching student/assessment/recommendation/
 * notification. The caller's identity arrives as X-User-Id, X-User-Role and X-User-Org-Id, injected
 * by api-gateway after it validates the JWT; this service never parses a token.
 *
 * Note there is no UserDetailsServiceAutoConfiguration exclude here. auth-service needs one because
 * it has the security starter and Boot would otherwise log a generated password; with no security
 * on this classpath the class is absent, and naming an absent class in spring.autoconfigure.exclude
 * fails startup outright.
 *
 * Consequence of trusting those headers: port 8087 must never be publicly reachable, or anyone can
 * send X-User-Role: SUPER_ADMIN and manage every organization in the system.
 */
@SpringBootApplication
public class OrganizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrganizationServiceApplication.class, args);
    }
}

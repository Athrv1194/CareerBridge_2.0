package com.careerbridge.gateway.config;

/**
 * Deliberately empty, and deliberately still here.
 *
 * There is no Spring Security on this service's classpath. Authentication is
 * JwtAuthenticationFilter, a plain servlet filter that validates the JWT and injects X-User-Id;
 * adding spring-boot-starter-security would bring a whole filter chain that we would immediately
 * have to permitAll, so it would be pure ceremony wrapped around the filter already doing the work.
 *
 * Kept as a signpost so the absence reads as a decision rather than an oversight -- same convention
 * as student-service's config/SecurityConfig.java. If method-level security or an OAuth2 resource
 * server is ever genuinely needed, this is where it goes.
 */
public class SecurityConfig {
}

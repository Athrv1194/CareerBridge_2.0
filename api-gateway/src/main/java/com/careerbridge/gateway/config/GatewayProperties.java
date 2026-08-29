package com.careerbridge.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Binds gateway.jwt-secret and gateway.public-paths from application.yml.
// Record components map kebab-case: jwtSecret → gateway.jwt-secret, publicPaths → gateway.public-paths.
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(String jwtSecret, List<String> publicPaths) {

    public GatewayProperties {
        // Null-guard: binding leaves publicPaths null when the key is absent, causing NPE in JwtAuthenticationFilter.
        // jwtSecret is NOT defaulted -- missing key means JwtUtil throws WeakKeyException at startup, which is correct.
        publicPaths = (publicPaths == null) ? List.of() : List.copyOf(publicPaths);
    }
}

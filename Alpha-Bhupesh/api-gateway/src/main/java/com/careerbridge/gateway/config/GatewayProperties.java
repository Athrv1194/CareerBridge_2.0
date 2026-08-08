package com.careerbridge.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the `gateway:` block of application.yml.
 *
 * A record rather than a @Data class: there is no Lombok on this service's classpath, and
 * @ConfigurationProperties binds records through their canonical constructor with no extra
 * annotation. Record components map to kebab-case keys, so jwtSecret reads gateway.jwt-secret and
 * publicPaths reads gateway.public-paths.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(String jwtSecret, List<String> publicPaths) {

    /**
     * publicPaths is null-guarded because binding leaves it null when the key is absent entirely,
     * and JwtAuthenticationFilter compiles the list into path patterns in its constructor. A null
     * there is an NPE during bean creation -- the context fails to start and contextLoads goes red,
     * which is a confusing way to learn that a yml key is missing.
     *
     * jwtSecret is deliberately NOT defaulted. If it is missing, Keys.hmacShaKeyFor throws in
     * JwtUtil's constructor and the application refuses to start, which is the correct outcome:
     * a gateway that cannot verify signatures must not accept traffic.
     */
    public GatewayProperties {
        publicPaths = (publicPaths == null) ? List.of() : List.copyOf(publicPaths);
    }
}

package com.careerbridge.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Frontend (Vite) and gateway run on different origins, so the browser needs an explicit CORS
 * permission slip before it will hand a response back to the page -- otherwise fetches like
 * GET /api/payment/plans fail with a CORS error even though the request itself succeeds.
 *
 * Registered via FilterRegistrationBean at HIGHEST_PRECEDENCE, same order as
 * JwtAuthenticationFilter. Boot collects FilterRegistrationBean-declared filters before plain
 * @Component Filter beans, and sorts with a stable comparator, so this filter's CORS headers
 * (including the OPTIONS preflight short-circuit) are applied before JwtAuthenticationFilter can
 * reject an unauthenticated preflight request. Do not convert this to a bare @Component Filter --
 * that reintroduces the ordering race.
 */
@Configuration
public class CorsConfig {

    /**
     * "http://localhost:*" (a Spring CORS origin *pattern*, not a literal origin) covers Vite's
     * auto-incrementing dev port -- 5173, 5174, whichever is free. The production origin is
     * injected via env, same ${VAR:default} convention as every other cross-service URL here.
     */
    @Value("${cors.allowed-origin:https://careerbridge.app}")
    private String prodOrigin;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", prodOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

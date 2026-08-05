package com.careerbridge.gateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Browser-originated requests (a frontend dev server, or a plain static HTML test page) need CORS
 * headers, which this gateway has never had -- every prior test against it was server-to-server
 * (curl, another service) and never hit a browser's same-origin policy at all.
 *
 * Registered via FilterRegistrationBean at HIGHEST_PRECEDENCE, not a bare @Bean CorsFilter with an
 * @Order annotation. Both claim the same precedence as JwtAuthenticationFilter's own
 * @Order(HIGHEST_PRECEDENCE), and Boot resolves that tie by registration order, not annotation
 * value: ServletContextInitializerBeans collects every FilterRegistrationBean first, then wraps
 * whatever plain Filter beans are left (JwtAuthenticationFilter, a @Component) and appends them --
 * so a plain @Bean Filter here loses the tie non-deterministically and JwtAuthenticationFilter can
 * run first. That is exactly what was happening: a CORS preflight is an OPTIONS request with no
 * Authorization header by definition, so JwtAuthenticationFilter 401'd it before Spring's CorsFilter
 * ever got a chance to answer, and the browser reported a failed preflight with no CORS headers at
 * all. Wrapping in FilterRegistrationBean puts this filter in the first-collected group, so it
 * reliably wins the tie and runs first. Verified live: OPTIONS and a real GET both now return
 * Access-Control-Allow-Origin, and a protected route's 401 carries CORS headers too, so the browser
 * surfaces the real error instead of a CORS failure.
 *
 * allowedOriginPatterns("*") rather than a fixed list: this project authenticates with a Bearer
 * token attached by frontend JavaScript, not a cookie, so there is no credentialed-CORS session to
 * protect by pinning an origin -- unlike a cookie-based app, a wildcard origin here does not let a
 * malicious page ride an already-authenticated session, since the malicious page would still need
 * to supply the token itself. allowCredentials stays false for exactly that reason: browsers
 * reject "*" combined with credentials outright, and this app sends none.
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

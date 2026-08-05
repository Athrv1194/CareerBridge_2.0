package com.careerbridge.auth.config;

import com.careerbridge.auth.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /refresh is public by necessity: it is called precisely because the
                        // access token expired, so requiring one here would make it unreachable.
                        .requestMatchers("/api/auth/register",
                                         "/api/auth/login",
                                         "/api/auth/refresh").permitAll()
                        // A caller with no account can't have a token either -- same reasoning as
                        // register/login/refresh above. api-gateway's own public-paths list already
                        // forwards these with no identity headers; this is the second, independent
                        // gate that was missed when the endpoints were added, which is why they 401'd
                        // with Spring Security's own default entry point rather than ever reaching
                        // AuthController.
                        .requestMatchers("/api/auth/forgot-password",
                                         "/api/auth/forgot-password/verify-otp",
                                         "/api/auth/forgot-password/reset").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Swagger UI / OpenAPI docs. Without these this filter chain's
                        // .anyRequest().authenticated() blocks /api-docs and /swagger-ui.html
                        // before the request ever reaches springdoc's controllers -- 401, not a
                        // gateway or routing problem. auth-service is the only one of the 5
                        // backend services with Spring Security on its classpath at all.
                        .requestMatchers("/api-docs/**",
                                         "/swagger-ui/**",
                                         "/swagger-ui.html",
                                         "/webjars/**").permitAll()
                        .anyRequest().authenticated())
                // Default entry point answers 403; 401 is the correct code for "no valid credentials".
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

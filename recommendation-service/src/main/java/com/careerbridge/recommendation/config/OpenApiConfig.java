package com.careerbridge.recommendation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recommendation Service API")
                        .version("1.0.0")
                        .description("CareerBridge Recommendation Service API"))
                // Overrides springdoc's request-derived default, which -- when this spec is
                // fetched by the gateway's aggregated Swagger UI via its /api-docs/recommendation
                // rewrite route -- would otherwise report this container's internal hostname
                // (http://recommendation-service:8084), unreachable from a browser. Every request
                // in this system, including "Try it out", is meant to go through the gateway.
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Via API Gateway")));
    }
}

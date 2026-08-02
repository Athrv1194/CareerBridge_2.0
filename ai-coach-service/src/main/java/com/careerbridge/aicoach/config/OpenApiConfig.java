package com.careerbridge.aicoach.config;

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
                        .title("AI Coach Service API")
                        .version("1.0.0")
                        .description("CareerBridge AI Coach Service API"))
                // Overrides springdoc's request-derived default, which -- when this spec is fetched
                // by the gateway's aggregated Swagger UI via its /api-docs/ai-coach rewrite route --
                // would otherwise report this container's internal hostname
                // (http://ai-coach-service:8092), unreachable from a browser.
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Via API Gateway")));
    }
}

package com.careerbridge.prs.config;

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
                        .title("Placement Readiness Score API")
                        .version("1.0.0")
                        .description("CareerBridge Placement Readiness Score API. Weighted composite "
                                + "of assessment (40%), roadmap (30%) and profile (20%) completion; "
                                + "the remaining 10% is reserved for the future resume builder, so "
                                + "totalScore currently tops out at 90."))
                // Overrides springdoc's request-derived default, which -- when this spec is fetched
                // by the gateway's aggregated Swagger UI via its /api-docs/prs rewrite route --
                // would otherwise report this container's internal hostname
                // (http://prs-service:8089), unreachable from a browser. Every request in this
                // system, including "Try it out", is meant to go through the gateway.
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Via API Gateway")));
    }
}

package com.careerbridge.mentor.config;

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
                        .title("Mentor Service API")
                        .version("1.0.0")
                        .description("CareerBridge Mentor Service. Mentor profiles, student-initiated "
                                + "1:1 session booking (REQUESTED -> ACCEPTED -> COMPLETED, or "
                                + "DECLINED/CANCELLED), and one student review per completed session "
                                + "feeding the mentor's average rating."))
                // Overrides springdoc's request-derived default, which -- when this spec is fetched
                // by the gateway's aggregated Swagger UI via its /api-docs/mentor rewrite route --
                // would otherwise report this container's internal hostname
                // (http://mentor-service:8094), unreachable from a browser. Every request in this
                // system, including "Try it out", is meant to go through the gateway.
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Via API Gateway")));
    }
}

package com.careerbridge.prs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The only synchronous outbound HTTP client in CareerBridge. Every other cross-service hop in this
 * system is a RabbitMQ event; this one exists because profileCompletionPercentage lives in
 * student-service and is published in no event.
 *
 * Timeouts are the whole reason this class exists rather than a one-line RestClient.create(url).
 * RestClient has NO default connect or read timeout, and fetchProfileScore runs on a
 * @RabbitListener thread at concurrency 1 -- so a student-service that accepts the connection and
 * then hangs would stall every subsequent PRS update indefinitely, with the queue backing up and
 * nothing in the logs. That is exactly the failure notification-service already documents for
 * JavaMail (connectiontimeout/timeout/writetimeout all pinned at 5000ms there, and CLAUDE.md says
 * why). A fail-soft call with no timeout is not fail-soft: it just relocates the failure from an
 * exception to a hang.
 *
 * 3 seconds each. student-service's /profile is a handful of indexed reads against a local
 * database; anything slower than 3s means it is unhealthy, and waiting longer only delays the
 * fallback to the last known profileScore.
 */
@Configuration
public class StudentClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Defaults to localhost:8082, NOT student-service:8082. The container hostname only resolves on
     * the compose network, so a Docker default would make every profile fetch fail with
     * UnknownHostException under `mvnw spring-boot:run` on a developer machine. docker-compose.yml
     * overrides STUDENT_SERVICE_URL to the container name. Same convention as api-gateway's
     * ${AUTH_SERVICE_URI:http://localhost:8081} placeholders.
     */
    @Bean
    public RestClient studentRestClient(@Value("${student.service.url:http://localhost:8082}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}

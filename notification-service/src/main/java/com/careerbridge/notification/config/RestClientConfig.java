package com.careerbridge.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * This service's first synchronous outbound HTTP client -- every other cross-service fact in
 * notification-service arrives as a RabbitMQ event. This one exists because the invoice PDF bytes
 * live in payment-service and there is no event payload large enough to carry them.
 *
 * Timeouts follow prs-service's StudentClientConfig exactly: RestClient has no default connect or
 * read timeout, and the fetch runs on a @RabbitListener thread at concurrency 1, so a hung
 * payment-service would otherwise stall every subsequent notification indefinitely -- the same
 * failure mode already documented for this service's own JavaMail timeouts.
 *
 * The @Bean method is named paymentRestClient, never paymentServiceClient -- that is the
 * component-scanned name of the @Service class it feeds below, and the collision is a
 * BeanDefinitionOverrideException at startup that no Mockito test can catch (recruiter-service
 * SEV-2, 2026-08-01). Do not rename it to match the client class.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Defaults to localhost:8093, NOT payment-service:8093 -- the container hostname only resolves
     * on the compose network, so a Docker default would break `mvnw spring-boot:run` on a developer
     * machine. docker-compose.yml overrides PAYMENT_SERVICE_URL to the container name.
     */
    @Bean
    public RestClient paymentRestClient(
            @Value("${payment.service.url:http://localhost:8093}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}

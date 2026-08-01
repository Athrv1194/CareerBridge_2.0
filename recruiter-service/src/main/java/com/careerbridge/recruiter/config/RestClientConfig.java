package com.careerbridge.recruiter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Two synchronous outbound HTTP clients, both against services reachable only inside the compose
 * network -- student-service for candidate profiles, prs-service for candidate scores. Neither
 * call goes through api-gateway: the gateway exists to validate JWTs from outside the system, and
 * these calls originate inside it carrying no token to validate.
 *
 * Timeouts are the whole reason this class exists rather than RestClient.create(url). RestClient
 * has NO default connect or read timeout, and both clients are called from a request thread -- a
 * hung downstream service would stall every candidate search or application submit indefinitely.
 * Same lesson as prs-service's StudentClientConfig and notification-service's JavaMail timeouts.
 * 3 seconds each: both target endpoints are a handful of indexed local-database reads.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Named studentRestClient, NOT studentServiceClient: the @Service class StudentServiceClient is
     * component-scanned under that second name, and a @Bean method of the same name collides with
     * it -- BeanDefinitionOverrideException at startup, which no unit test can see because none of
     * them build a Spring context. Same convention as prs-service's StudentClientConfig. Do not
     * rename these to match the client classes.
     *
     * Defaults to localhost:8082, NOT student-service:8082 -- the container hostname only resolves
     * on the compose network. docker-compose overrides STUDENT_SERVICE_URL.
     */
    @Bean
    public RestClient studentRestClient(@Value("${student.service.url:http://localhost:8082}") String url) {
        return buildClient(url);
    }

    /** Defaults to localhost:8089, NOT prs-service:8089. docker-compose overrides PRS_SERVICE_URL. */
    @Bean
    public RestClient prsRestClient(@Value("${prs.service.url:http://localhost:8089}") String url) {
        return buildClient(url);
    }

    private RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}

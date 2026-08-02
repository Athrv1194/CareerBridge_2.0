package com.careerbridge.resume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The one synchronous outbound HTTP client in this service, calling student-service directly on
 * the compose network -- never through api-gateway. Same reasoning as prs-service's
 * StudentClientConfig: RestClient has no default connect or read timeout, and fetchMyProfile runs
 * on a request thread, so a student-service that accepts the connection and hangs would stall
 * every resume generation indefinitely. 3 seconds each, matching every other cross-service client
 * in this project.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Named studentRestClient, NOT studentServiceClient: the @Service class StudentServiceClient is
     * component-scanned under that second name, and a @Bean method of the same name collides with
     * it -- BeanDefinitionOverrideException at startup, invisible to every unit test since none of
     * them build a Spring context. Cost recruiter-service a SEV-2, caught only by contextLoads. Do
     * not rename this to match the client class.
     *
     * Defaults to localhost:8082, NOT student-service:8082 -- the container hostname only resolves
     * on the compose network. docker-compose overrides STUDENT_SERVICE_URL.
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

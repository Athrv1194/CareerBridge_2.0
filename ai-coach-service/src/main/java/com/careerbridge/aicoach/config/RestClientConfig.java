package com.careerbridge.aicoach.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Six outbound HTTP clients, two timeout tiers. The three internal ones (student/prs/roadmap-service)
 * are indexed local-database reads inside the compose network, same 3s/3s convention as every other
 * cross-service client in this project (prs-service's StudentClientConfig, recruiter-service's
 * RestClientConfig above). The three external ones (Groq/Tavily/YouTube) are real third-party APIs
 * over the public internet and get a longer read timeout -- 5s connect / 10s read.
 *
 * Bean naming is load-bearing: none of these names may match a component-scanned @Service class.
 * StudentServiceClient, PrsServiceClient, RoadmapServiceClient, GroqClient, TavilyClient and
 * YouTubeClient are all @Service classes elsewhere in this package tree -- a @Bean method sharing
 * any of those names is a BeanDefinitionOverrideException at startup, invisible to every unit test
 * because none of them build a Spring context. This exact bug shipped in recruiter-service (SEV-2,
 * 2026-08-01) and was only caught by contextLoads. Hence *RestClient suffixes throughout, never
 * *ServiceClient or the bare provider name.
 */
@Configuration
public class RestClientConfig {

    private static final Duration INTERNAL_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration INTERNAL_READ_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration EXTERNAL_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXTERNAL_READ_TIMEOUT = Duration.ofSeconds(10);

    /** Defaults to localhost:8082, NOT student-service:8082 -- docker-compose overrides STUDENT_SERVICE_URL. */
    @Bean
    public RestClient studentRestClient(@Value("${student.service.url:http://localhost:8082}") String url) {
        return buildClient(url, INTERNAL_CONNECT_TIMEOUT, INTERNAL_READ_TIMEOUT);
    }

    /** Defaults to localhost:8089, NOT prs-service:8089. docker-compose overrides PRS_SERVICE_URL. */
    @Bean
    public RestClient prsRestClient(@Value("${prs.service.url:http://localhost:8089}") String url) {
        return buildClient(url, INTERNAL_CONNECT_TIMEOUT, INTERNAL_READ_TIMEOUT);
    }

    /** Defaults to localhost:8088, NOT roadmap-service:8088. docker-compose overrides ROADMAP_SERVICE_URL. */
    @Bean
    public RestClient roadmapRestClient(@Value("${roadmap.service.url:http://localhost:8088}") String url) {
        return buildClient(url, INTERNAL_CONNECT_TIMEOUT, INTERNAL_READ_TIMEOUT);
    }

    /** No baseUrl -- Groq's chat-completions endpoint is a full URL from config, not a path off a base. */
    @Bean
    public RestClient groqRestClient() {
        return buildClient(null, EXTERNAL_CONNECT_TIMEOUT, EXTERNAL_READ_TIMEOUT);
    }

    @Bean
    public RestClient tavilyRestClient(@Value("${tavily.api.url}") String url) {
        return buildClient(url, EXTERNAL_CONNECT_TIMEOUT, EXTERNAL_READ_TIMEOUT);
    }

    /** No baseUrl -- YouTube's search URL plus query params is built as one full URL string per call. */
    @Bean
    public RestClient youtubeRestClient() {
        return buildClient(null, EXTERNAL_CONNECT_TIMEOUT, EXTERNAL_READ_TIMEOUT);
    }

    private RestClient buildClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}

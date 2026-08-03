package com.careerbridge.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The one outbound HTTP client in this service, calling Razorpay over the public internet.
 *
 * Timeouts are the external tier -- 5s connect, 10s read -- matching ai-coach-service's Groq,
 * Tavily and YouTube clients rather than the 3s/3s used for calls that stay inside the compose
 * network. RestClient has NO default timeout of any kind, and createOrder runs on a request
 * thread, so a Razorpay outage that accepts the connection and then hangs would tie up a Tomcat
 * worker indefinitely.
 *
 * Auth is HTTP Basic with the key id as username and the key secret as password, which is exactly
 * what Razorpay's docs specify (curl -u KEY_ID:KEY_SECRET). setBasicAuth does the base64 for us --
 * no commons-codec needed.
 */
@Configuration
public class RazorpayClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Named razorpayRestClient, NOT razorpayClient: the @Service class RazorpayClient is
     * component-scanned under that second name, and a @Bean method of the same name collides with
     * it -- BeanDefinitionOverrideException at startup, invisible to every unit test because none
     * of them build a Spring context. That exact bug cost recruiter-service a SEV-2 and was caught
     * only by contextLoads. Do not rename this to match the client class.
     */
    @Bean
    public RestClient razorpayRestClient(
            @Value("${razorpay.api.url:https://api.razorpay.com/v1}") String baseUrl,
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);

        // Only attach credentials when they exist. With blank keys the client still builds (so the
        // context loads on a fresh clone with no sandbox keys) and RazorpayClient's own hasText
        // guard refuses the call with a clear 503 before any request is sent.
        if (!keyId.isBlank() && !keySecret.isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret));
        }

        return builder.build();
    }
}

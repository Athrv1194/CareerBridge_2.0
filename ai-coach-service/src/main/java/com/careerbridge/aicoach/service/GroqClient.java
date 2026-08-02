package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.external.GroqDto;
import com.careerbridge.aicoach.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Unlike Tavily/YouTube, Groq failure is user-facing: it throws CustomException 503 rather than
 * returning a sentinel, because a chat message with no reply is a broken feature, not a degraded
 * one. Checked for a blank key BEFORE calling out, so a fresh clone with no GROQ_API_KEY yet fails
 * fast with a clear message instead of sending "Authorization: Bearer " and getting an opaque 401.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private static final double TEMPERATURE = 0.7;
    private static final int MAX_TOKENS = 1024;

    private final RestClient groqRestClient;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    public GroqClient(@Qualifier("groqRestClient") RestClient groqRestClient) {
        this.groqRestClient = groqRestClient;
    }

    public String chat(List<Map<String, String>> messages) {
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(
                    "AI coach is not configured yet - please try again later",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            GroqDto.Request request = GroqDto.Request.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(TEMPERATURE)
                    .maxTokens(MAX_TOKENS)
                    .build();

            GroqDto.Response response = groqRestClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(GroqDto.Response.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new IllegalStateException("Groq returned no choices");
            }

            GroqDto.ResponseMessage message = response.getChoices().get(0).getMessage();
            if (message == null || message.getContent() == null) {
                throw new IllegalStateException("Groq returned an empty message");
            }
            return message.getContent();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // Never log the request body -- it carries the assembled system prompt and, upstream
            // of this class, the student's own profile data.
            log.error("Groq API call failed: {}", e.getMessage());
            throw new CustomException(
                    "AI coach is temporarily unavailable - please try again later",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}

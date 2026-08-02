package com.careerbridge.aicoach.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Groq's OpenAI-compatible chat-completions request and response wire shapes. Unverified against a
 * live call; see the note on TavilyDto.
 */
public class GroqDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String model;
        // Each element is {"role": "...", "content": "..."} -- a Map here, not a typed record,
        // because both system/user/assistant turns share the exact same two-key shape and Groq's
        // wire format is the OpenAI convention, not this project's own ChatMessage entity.
        private List<Map<String, String>> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<Choice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private ResponseMessage message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMessage {
        private String role;
        private String content;
    }

    private GroqDto() {
    }
}

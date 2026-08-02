package com.careerbridge.aicoach.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tavily's /search request and response wire shapes, kept in one file since neither is used
 * anywhere else. Unverified against a live call -- no Tavily API key exists yet at the time this
 * was written. Kept isolated here so a wire-shape correction on first real use is a single-file
 * edit; see AiCoachConstants / ai_incident_log.md if this needs correcting.
 */
public class TavilyDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @JsonProperty("api_key")
        private String apiKey;
        private String query;
        @JsonProperty("max_results")
        private int maxResults;
        @JsonProperty("search_depth")
        private String searchDepth;
        @JsonProperty("include_domains")
        private List<String> includeDomains;
        @JsonProperty("exclude_domains")
        private List<String> excludeDomains;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<Result> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String title;
        private String url;
    }

    private TavilyDto() {
    }
}

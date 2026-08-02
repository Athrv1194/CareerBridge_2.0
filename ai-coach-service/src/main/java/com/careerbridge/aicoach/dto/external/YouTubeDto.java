package com.careerbridge.aicoach.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * YouTube Data API v3 search response wire shape. Request is a plain GET with query params, so
 * there is no Request DTO here -- only the response needs a class. Unverified against a live call;
 * see the note on TavilyDto.
 */
public class YouTubeDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<Item> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Id id;
        private Snippet snippet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id {
        private String videoId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Snippet {
        private String title;
        private String channelTitle;
    }

    private YouTubeDto() {
    }
}

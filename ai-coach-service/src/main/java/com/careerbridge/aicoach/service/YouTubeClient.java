package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.external.YouTubeDto;
import com.careerbridge.aicoach.model.ResourceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Returns null on any failure -- caller (CatalogRefresher) null-checks before adding to the
 * resource list. Never throws.
 */
@Component
public class YouTubeClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);

    private final RestClient youtubeRestClient;

    @Value("${youtube.api.key:}")
    private String apiKey;

    @Value("${youtube.api.search.url}")
    private String searchUrl;

    public YouTubeClient(@Qualifier("youtubeRestClient") RestClient youtubeRestClient) {
        this.youtubeRestClient = youtubeRestClient;
    }

    public ResourceItem searchVideo(String milestoneTitle, String careerName) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("YOUTUBE_API_KEY is not set; skipping YouTube search for '{}'", milestoneTitle);
            return null;
        }

        try {
            String query = milestoneTitle + " " + careerName + " tutorial for beginners";
            String uri = searchUrl + "?part=snippet&type=video&maxResults=1"
                    + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&key=" + apiKey;

            YouTubeDto.Response response = youtubeRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(YouTubeDto.Response.class);

            if (response == null || isEmpty(response.getItems())) {
                return null;
            }

            YouTubeDto.Item item = response.getItems().get(0);
            if (item.getId() == null || item.getId().getVideoId() == null || item.getSnippet() == null) {
                return null;
            }

            String title = item.getSnippet().getTitle() + " - " + item.getSnippet().getChannelTitle();
            return ResourceItem.builder()
                    .title(title)
                    .url("https://www.youtube.com/watch?v=" + item.getId().getVideoId())
                    .type("video")
                    .platform("YouTube")
                    .build();
        } catch (Exception e) {
            log.warn("YouTube search failed for milestone='{}': {}", milestoneTitle, e.getMessage());
            return null;
        }
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}

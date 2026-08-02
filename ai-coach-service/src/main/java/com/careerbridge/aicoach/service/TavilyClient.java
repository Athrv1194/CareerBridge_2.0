package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.external.TavilyDto;
import com.careerbridge.aicoach.model.ResourceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Never throws, never returns null -- List.of() on any failure, including a blank API key. The
 * caller (CatalogRefresher) treats "Tavily found nothing" and "Tavily is down" identically: try
 * YouTube anyway, and only skip persisting if both come back empty (see the R2 rule in the plan).
 */
@Component
public class TavilyClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyClient.class);

    private static final int MAX_RESULTS = 4;

    private static final List<String> INCLUDE_DOMAINS = List.of(
            "geeksforgeeks.org", "baeldung.com", "freecodecamp.org",
            "w3schools.com", "docs.oracle.com", "tutorialspoint.com",
            "javatpoint.com", "coursera.org", "dev.to", "medium.com"
    );

    private static final Map<String, String[]> DOMAIN_META = new HashMap<>();

    static {
        DOMAIN_META.put("geeksforgeeks.org", new String[]{"article", "GeeksforGeeks"});
        DOMAIN_META.put("baeldung.com", new String[]{"article", "Baeldung"});
        DOMAIN_META.put("freecodecamp.org", new String[]{"tutorial", "freeCodeCamp"});
        DOMAIN_META.put("w3schools.com", new String[]{"tutorial", "W3Schools"});
        DOMAIN_META.put("docs.oracle.com", new String[]{"documentation", "Oracle Docs"});
        DOMAIN_META.put("tutorialspoint.com", new String[]{"tutorial", "Tutorialspoint"});
        DOMAIN_META.put("javatpoint.com", new String[]{"tutorial", "Javatpoint"});
        DOMAIN_META.put("coursera.org", new String[]{"course", "Coursera"});
        DOMAIN_META.put("dev.to", new String[]{"article", "Dev.to"});
        DOMAIN_META.put("medium.com", new String[]{"article", "Medium"});
    }

    private final RestClient tavilyRestClient;

    @Value("${tavily.api.key:}")
    private String apiKey;

    public TavilyClient(@Qualifier("tavilyRestClient") RestClient tavilyRestClient) {
        this.tavilyRestClient = tavilyRestClient;
    }

    public List<ResourceItem> searchResources(String milestoneTitle, String careerName) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("TAVILY_API_KEY is not set; skipping Tavily search for '{}'", milestoneTitle);
            return List.of();
        }

        try {
            TavilyDto.Request request = TavilyDto.Request.builder()
                    .apiKey(apiKey)
                    .query(milestoneTitle + " " + careerName + " tutorial article documentation")
                    .maxResults(MAX_RESULTS)
                    .searchDepth("basic")
                    .includeDomains(INCLUDE_DOMAINS)
                    .excludeDomains(List.of("youtube.com"))
                    .build();

            TavilyDto.Response response = tavilyRestClient.post()
                    .uri("/search")
                    .body(request)
                    .retrieve()
                    .body(TavilyDto.Response.class);

            if (response == null || response.getResults() == null) {
                return List.of();
            }

            List<ResourceItem> resources = new ArrayList<>();
            for (TavilyDto.Result result : response.getResults()) {
                ResourceItem item = toResource(result);
                if (item != null) {
                    resources.add(item);
                }
                if (resources.size() >= MAX_RESULTS) {
                    break;
                }
            }
            return resources;
        } catch (Exception e) {
            log.warn("Tavily search failed for milestone='{}': {}", milestoneTitle, e.getMessage());
            return List.of();
        }
    }

    private ResourceItem toResource(TavilyDto.Result result) {
        if (result == null || result.getTitle() == null || result.getUrl() == null) {
            return null;
        }
        String domain = domainOf(result.getUrl());
        String[] meta = DOMAIN_META.getOrDefault(domain, new String[]{"article", domain});
        return ResourceItem.builder()
                .title(result.getTitle())
                .url(result.getUrl())
                .type(meta[0])
                .platform(meta[1])
                .build();
    }

    /**
     * URI.create, NOT new java.net.URL(String) -- URL(String) is @Deprecated(since="20"). Two
     * failure modes to handle, not one, confirmed by probing Temurin 21: URI.create throws
     * IllegalArgumentException on syntactically invalid input, AND getHost() returns null for a
     * relative string or a host containing an underscore (RFC 2396 host grammar).
     */
    static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return url;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}

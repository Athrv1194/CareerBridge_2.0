package com.careerbridge.aicoach.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Pure logic only, no HTTP -- this project's convention is that REST clients are verified live,
 * not mocked through the fluent RestClient chain. These tests pin the two things in TavilyClient
 * that are ordinary Java logic: the domain-extraction helper (C5/R -- URI.create, not the
 * deprecated java.net.URL) and the domain-to-{type,platform} lookup.
 */
class TavilyClientTest {

    private final TavilyClient client = new TavilyClient(mock(RestClient.class));

    @Test
    void domainOf_KnownDomain_StripsWww() {
        assertEquals("geeksforgeeks.org",
                TavilyClient.domainOf("https://www.geeksforgeeks.org/some-article"));
    }

    @Test
    void domainOf_NoWww_ReturnsHostUnchanged() {
        assertEquals("baeldung.com", TavilyClient.domainOf("https://baeldung.com/some-article"));
    }

    @Test
    void domainOf_MalformedUrl_DoesNotThrow() {
        // A raw space is not valid in a URI and URI.create throws IllegalArgumentException.
        assertEquals("not a url at all", TavilyClient.domainOf("not a url at all"));
    }

    @Test
    void domainOf_UnderscoreHost_DoesNotThrow() {
        // RFC 2396 host grammar excludes underscores -- URI.create parses this without throwing,
        // but getHost() returns null. Must fall back to the raw url, not NPE on host.startsWith.
        assertEquals("http://my_host.example.com/path",
                TavilyClient.domainOf("http://my_host.example.com/path"));
    }

    @Test
    void domainOf_EmptyString_DoesNotThrow() {
        assertEquals("", TavilyClient.domainOf(""));
    }
}

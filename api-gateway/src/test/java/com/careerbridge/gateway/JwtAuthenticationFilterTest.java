package com.careerbridge.gateway;

import com.careerbridge.gateway.config.GatewayProperties;
import com.careerbridge.gateway.constants.GatewayConstants;
import com.careerbridge.gateway.filter.JwtAuthenticationFilter;
import com.careerbridge.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests -- no Spring context, no broker, no downstream service.
 *
 * MockFilterChain.doFilter is single-use, so every test constructs a fresh one. Asserting on
 * chain.getRequest() is what proves what the filter actually passed downstream: that object is the
 * wrapper the gateway would go on to proxy.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "your-256-bit-secret-key-here-change-in-production";
    private static final String OTHER_SECRET = "a-completely-different-secret-that-is-long-enough-xx";

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties(
                SECRET,
                List.of("/api/auth/**", "/actuator/**", "/api/recommendation/careers"));
        filter = new JwtAuthenticationFilter(new JwtUtil(properties), properties);
    }

    private static String token(String secret, long userId, long ttlMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("ada@careerbridge.com")
                .claim("userId", userId)
                .claim("role", "STUDENT")
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key)
                .compact();
    }

    private static String validToken() {
        return token(SECRET, 42L, 900_000);
    }

    /** The header as the downstream service would receive it. */
    private static String forwardedUserId(MockFilterChain chain) {
        return ((HttpServletRequest) chain.getRequest()).getHeader(GatewayConstants.USER_ID_HEADER);
    }

    @Test
    @DisplayName("public path: passes through with no token at all")
    void filter_PublicPath_SkipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "the request must reach the chain");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("missing Authorization header: 401 with the JSON error contract")
    void filter_MissingAuthHeader_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        // startsWith, not equals: the filter also sets the charset, so this reads
        // "application/json;charset=UTF-8".
        assertTrue(response.getContentType().startsWith("application/json"),
                response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\":401"),
                response.getContentAsString());
        assertTrue(response.getContentAsString().contains(GatewayConstants.ERROR_MISSING_TOKEN));
        assertNull(chain.getRequest(), "the request must not reach the chain");
    }

    @Test
    @DisplayName("wrong scheme: Basic instead of Bearer is 401")
    void filter_InvalidBearerPrefix_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("valid token: X-User-Id is injected for the downstream service")
    void filter_ValidToken_AddsUserIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + validToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("42", forwardedUserId(chain));

        // All three accessors must agree. Spring's ServletRequestHeadersAdapter -- which builds the
        // headers the gateway proxies onward -- enumerates via getHeaderNames() then reads
        // getHeaders(name), so overriding only getHeader(String) would be a silent no-op and the
        // header would never leave the gateway.
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("42",
                Collections.list(forwarded.getHeaders(GatewayConstants.USER_ID_HEADER)).get(0));
        assertTrue(Collections.list(forwarded.getHeaderNames()).stream()
                        .anyMatch(GatewayConstants.USER_ID_HEADER::equalsIgnoreCase),
                "X-User-Id must appear in getHeaderNames()");
    }

    @Test
    @DisplayName("expired token: 401 saying so specifically, so the frontend can refresh")
    void filter_ExpiredToken_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + token(SECRET, 42L, -60_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(GatewayConstants.ERROR_EXPIRED_TOKEN),
                response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("tampered token: a genuine byte change is rejected")
    void filter_TamperedToken_Returns401() throws Exception {
        String[] parts = validToken().split("\\.");
        // Flip a payload character, which changes what was signed. Appending a character to the
        // signature would NOT work as a tamper: a 48-byte HS384 signature is exactly 64 base64url
        // chars, so a 65th decodes to the same bytes and the token still verifies.
        String tampered = parts[0] + "." + ("Z" + parts[1].substring(1)) + "." + parts[2];

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + tampered);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(GatewayConstants.ERROR_INVALID_TOKEN));
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("token signed with a different secret: 401")
    void filter_WrongSecretToken_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + token(OTHER_SECRET, 42L, 900_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("spoofing on a protected path: the client's X-User-Id is overwritten by the token's")
    void filter_SpoofedUserIdHeader_IsOverwritten() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + validToken());
        request.addHeader(GatewayConstants.USER_ID_HEADER, "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("42", forwardedUserId(chain), "the token wins, never the client's header");
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeaders(GatewayConstants.USER_ID_HEADER))
                .contains("999"));
    }

    @Test
    @DisplayName("spoofing on a public path: the client's X-User-Id is stripped, not forwarded")
    void filter_SpoofedUserIdOnPublicPath_IsStripped() throws Exception {
        // The one that fails if anyone "optimises" the public branch into a bare
        // chain.doFilter(request, response). Downstream services trust X-User-Id blindly and have
        // no Spring Security, so forwarding the raw request here is full impersonation via curl.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recommendation/careers");
        request.addHeader(GatewayConstants.USER_ID_HEADER, "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "public paths still reach the chain");
        assertNull(forwardedUserId(chain), "a client-supplied id must never survive");
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest()).getHeaderNames()).stream()
                        .anyMatch(GatewayConstants.USER_ID_HEADER::equalsIgnoreCase),
                "X-User-Id must not appear in getHeaderNames() either");
    }

    @Test
    @DisplayName("public path matching is pattern-based, not a prefix: a near-miss stays protected")
    void filter_PathSimilarToPublicOne_IsStillProtected() throws Exception {
        // startsWith() matching would make this public. PathPattern does not.
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/recommendation/careers-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }
}

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
        // Mirrors application.yml exactly. The three auth entries are listed individually rather
        // than as /api/auth/** on purpose: the wildcard made /api/auth/admin/** public, and a
        // public path is forwarded with no identity headers, so the admin endpoints could never
        // receive the X-User-Role they authorize on.
        GatewayProperties properties = new GatewayProperties(
                SECRET,
                List.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                        "/actuator/**", "/api/recommendation/careers"));
        filter = new JwtAuthenticationFilter(new JwtUtil(properties), properties);
    }

    private static String token(String secret, long userId, long ttlMillis) {
        return token(secret, userId, "STUDENT", null, ttlMillis);
    }

    /**
     * orgId is a Long rather than a long precisely so it can be null: auth-service's
     * User.organizationId is nullable, and jjwt omits a null claim entirely, which is exactly the
     * shape a SUPER_ADMIN's token has in production.
     */
    private static String token(String secret, long userId, String role, Long orgId, long ttlMillis) {
        return token(secret, userId, role, orgId, null, ttlMillis);
    }

    /**
     * plan is nullable for the same reason orgId is: auth-service only started writing the claim
     * when payment-service landed, so a token minted before that -- or for a User row with a null
     * subscriptionPlan -- legitimately carries no plan at all, and jjwt omits a null claim entirely.
     */
    private static String token(String secret, long userId, String role, Long orgId,
                                String plan, long ttlMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("ada@careerbridge.com")
                .claim("userId", userId)
                .claim("role", role)
                .claim("organizationId", orgId)
                .claim("plan", plan)
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

    private static String forwardedRole(MockFilterChain chain) {
        return ((HttpServletRequest) chain.getRequest()).getHeader(GatewayConstants.USER_ROLE_HEADER);
    }

    private static String forwardedOrgId(MockFilterChain chain) {
        return ((HttpServletRequest) chain.getRequest()).getHeader(GatewayConstants.USER_ORG_ID_HEADER);
    }

    private static String forwardedPlan(MockFilterChain chain) {
        return ((HttpServletRequest) chain.getRequest()).getHeader(GatewayConstants.USER_PLAN_HEADER);
    }

    @Test
    @DisplayName("valid token: the subscription plan is injected as X-User-Plan")
    void filter_ValidToken_AddsPlanHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai-coach/resources");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, GatewayConstants.BEARER_PREFIX
                + token(SECRET, 21L, "STUDENT", null, "STUDENT_PREMIUM", 900_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("STUDENT_PREMIUM", forwardedPlan(chain));

        // Same all-three-accessors rule as the other identity headers: getHeaders/getHeaderNames
        // are what the gateway's proxy step actually reads.
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("STUDENT_PREMIUM",
                Collections.list(forwarded.getHeaders(GatewayConstants.USER_PLAN_HEADER)).get(0));
        assertTrue(Collections.list(forwarded.getHeaderNames()).stream()
                        .anyMatch(GatewayConstants.USER_PLAN_HEADER::equalsIgnoreCase),
                "X-User-Plan must appear in getHeaderNames()");
    }

    @Test
    @DisplayName("token with no plan claim: the header is absent, not empty")
    void filter_TokenWithoutPlan_OmitsPlanHeader() throws Exception {
        // Every token minted before payment-service landed has no plan claim. Forwarding "" would
        // look to a downstream service like a real plan named "".
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, GatewayConstants.BEARER_PREFIX
                + token(SECRET, 42L, "STUDENT", null, null, 900_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(forwardedPlan(chain));
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeaders(GatewayConstants.USER_PLAN_HEADER))
                        .iterator().hasNext(),
                "getHeaders must be empty, not a single empty string");
    }

    @Test
    @DisplayName("spoofed X-User-Plan on a protected path is overwritten by the token's value")
    void filter_SpoofedPlanHeader_IsOverwritten() throws Exception {
        // The half of "inject and strip" that gets forgotten. Nothing reads this header yet, which
        // is exactly why it has to be managed from day one: the day a service starts gating on it,
        // an unmanaged header would make every caller premium for free.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai-coach/resources");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, GatewayConstants.BEARER_PREFIX
                + token(SECRET, 21L, "STUDENT", null, "FREE", 900_000));
        request.addHeader(GatewayConstants.USER_PLAN_HEADER, "STUDENT_PREMIUM");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("FREE", forwardedPlan(chain), "the token's plan must win, never the client's");
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        List<String> values = Collections.list(forwarded.getHeaders(GatewayConstants.USER_PLAN_HEADER));
        assertEquals(1, values.size(), "the spoofed value must not survive alongside the real one");
        assertEquals("FREE", values.get(0));
    }

    @Test
    @DisplayName("spoofed X-User-Plan on a PUBLIC path is stripped entirely")
    void filter_SpoofedPlanHeaderOnPublicPath_IsStripped() throws Exception {
        // A public path is forwarded with no identity at all, so there is no replacement value --
        // membership in MANAGED_HEADERS is the only thing removing the client's header here.
        // Uses an already-public path deliberately: this asserts the stripping property itself, not
        // the payment-service route, so it stays valid however public-paths later changes.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(GatewayConstants.USER_PLAN_HEADER, "COLLEGE_PRO");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(forwardedPlan(chain));
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeaders(GatewayConstants.USER_PLAN_HEADER))
                        .iterator().hasNext(),
                "a client-supplied plan must not survive a public path");
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
    @DisplayName("spoofing on a protected path: the client's identity headers are all overwritten")
    void filter_SpoofedUserIdHeader_IsOverwritten() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/profile");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + validToken());
        request.addHeader(GatewayConstants.USER_ID_HEADER, "999");
        // The privilege-escalation attempt organization-service's RBAC depends on us blocking: a
        // STUDENT token plus a hand-set SUPER_ADMIN role. Only the token may decide the role.
        request.addHeader(GatewayConstants.USER_ROLE_HEADER, "SUPER_ADMIN");
        request.addHeader(GatewayConstants.USER_ORG_ID_HEADER, "777");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("42", forwardedUserId(chain), "the token wins, never the client's header");
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeaders(GatewayConstants.USER_ID_HEADER))
                .contains("999"));

        assertEquals("STUDENT", forwardedRole(chain),
                "a client-supplied role must never survive; the token said STUDENT");
        // validToken() carries no organizationId, so the spoofed 777 must leave no trace at all.
        assertNull(forwardedOrgId(chain), "a client-supplied org id must never survive");
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest()).getHeaderNames()).stream()
                        .anyMatch(GatewayConstants.USER_ORG_ID_HEADER::equalsIgnoreCase),
                "X-User-Org-Id must not appear in getHeaderNames() when the token has no org");
    }

    @Test
    @DisplayName("spoofing on a public path: every identity header is stripped, not forwarded")
    void filter_SpoofedUserIdOnPublicPath_IsStripped() throws Exception {
        // The one that fails if anyone "optimises" the public branch into a bare
        // chain.doFilter(request, response). Downstream services trust these headers blindly and
        // have no Spring Security, so forwarding the raw request here is full impersonation via
        // curl -- and, since X-User-Role landed, privilege escalation rather than just identity
        // theft.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recommendation/careers");
        request.addHeader(GatewayConstants.USER_ID_HEADER, "999");
        request.addHeader(GatewayConstants.USER_ROLE_HEADER, "SUPER_ADMIN");
        request.addHeader(GatewayConstants.USER_ORG_ID_HEADER, "777");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "public paths still reach the chain");
        assertNull(forwardedUserId(chain), "a client-supplied id must never survive");
        assertNull(forwardedRole(chain), "a client-supplied role must never survive");
        assertNull(forwardedOrgId(chain), "a client-supplied org id must never survive");

        List<String> names = Collections.list(
                ((HttpServletRequest) chain.getRequest()).getHeaderNames());
        assertFalse(names.stream().anyMatch(GatewayConstants.USER_ID_HEADER::equalsIgnoreCase),
                "X-User-Id must not appear in getHeaderNames() either");
        assertFalse(names.stream().anyMatch(GatewayConstants.USER_ROLE_HEADER::equalsIgnoreCase),
                "X-User-Role must not appear in getHeaderNames() either");
        assertFalse(names.stream().anyMatch(GatewayConstants.USER_ORG_ID_HEADER::equalsIgnoreCase),
                "X-User-Org-Id must not appear in getHeaderNames() either");
    }

    @Test
    @DisplayName("valid token: role and org id are injected alongside the user id")
    void filter_ValidToken_AddsRoleAndOrgIdHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/organization/1");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, GatewayConstants.BEARER_PREFIX
                + token(SECRET, 7L, "ORG_ADMIN", 3L, 900_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("7", forwardedUserId(chain));
        assertEquals("ORG_ADMIN", forwardedRole(chain));
        assertEquals("3", forwardedOrgId(chain));

        // Same all-three-accessors rule as the user id: getHeaders/getHeaderNames are what the
        // gateway's proxy step actually reads, so injecting via getHeader alone would be invisible.
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("ORG_ADMIN",
                Collections.list(forwarded.getHeaders(GatewayConstants.USER_ROLE_HEADER)).get(0));
        assertEquals("3",
                Collections.list(forwarded.getHeaders(GatewayConstants.USER_ORG_ID_HEADER)).get(0));

        List<String> names = Collections.list(forwarded.getHeaderNames());
        assertTrue(names.stream().anyMatch(GatewayConstants.USER_ROLE_HEADER::equalsIgnoreCase),
                "X-User-Role must appear in getHeaderNames()");
        assertTrue(names.stream().anyMatch(GatewayConstants.USER_ORG_ID_HEADER::equalsIgnoreCase),
                "X-User-Org-Id must appear in getHeaderNames()");
    }

    @Test
    @DisplayName("token with no organizationId: the header is absent, not empty")
    void filter_TokenWithoutOrgId_OmitsOrgIdHeader() throws Exception {
        // A SUPER_ADMIN belongs to no organization. Forwarding "" or "null" here would make
        // @RequestHeader(required = false) Long bind-fail with a 400 instead of handing the service
        // a clean null, so the header must genuinely not be present.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/organization");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER, GatewayConstants.BEARER_PREFIX
                + token(SECRET, 1L, "SUPER_ADMIN", null, 900_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("SUPER_ADMIN", forwardedRole(chain));
        assertNull(forwardedOrgId(chain));
        assertFalse(Collections.list(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeaders(GatewayConstants.USER_ORG_ID_HEADER))
                        .iterator().hasNext(),
                "getHeaders must be empty, not a single empty string");
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

    // ---------------------------------------------------------------------------------------------
    // ADMIN MODULE: public-paths was narrowed from the /api/auth/** wildcard to three explicit
    // entries. These pin both halves of that change.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("admin auth path with no token: 401, because /api/auth/** is no longer a wildcard")
    void filter_AdminAuthPathWithoutToken_IsProtected() throws Exception {
        // Under the old /api/auth/** wildcard this path was PUBLIC, and a public path is forwarded
        // with identityHeaders(null, null, null) -- so it reached auth-service with no X-User-Role
        // and 400'd on the required header, making every admin endpoint permanently unreachable.
        // A 200 here would mean the wildcard is back.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "a protected path must not reach the chain without a token");
    }

    @Test
    @DisplayName("admin auth path with a valid token: the role is injected for the service to authorize on")
    void filter_AdminAuthPathWithToken_InjectsRole() throws Exception {
        // The other half: reaching auth-service is not enough, the role header has to arrive too.
        // AdminUserController reads X-User-Role as a required header and AdminUserService is what
        // turns it into a 403 for a non-admin.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/admin/users");
        request.addHeader(GatewayConstants.AUTHORIZATION_HEADER,
                GatewayConstants.BEARER_PREFIX + token(SECRET, 42L, "SUPER_ADMIN", null, 60_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("42", forwardedUserId(chain));
        assertEquals("SUPER_ADMIN", forwardedRole(chain));
    }

    @Test
    @DisplayName("login stays public after the narrowing")
    void filter_LoginStillPublic() throws Exception {
        // The narrowing must not cost the endpoints that genuinely cannot carry a token.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "register must still pass through with no token");
    }

    @Test
    @DisplayName("refresh stays public: refresh tokens are opaque UUIDs this gateway cannot verify")
    void filter_RefreshStillPublic() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "refresh is called because the access token expired");
    }
}

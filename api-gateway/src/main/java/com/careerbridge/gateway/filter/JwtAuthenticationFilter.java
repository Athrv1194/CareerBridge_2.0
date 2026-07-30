package com.careerbridge.gateway.filter;

import com.careerbridge.gateway.config.GatewayProperties;
import com.careerbridge.gateway.constants.GatewayConstants;
import com.careerbridge.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The only place in CareerBridge where a JWT is verified.
 *
 * A plain servlet filter rather than a Spring Cloud Gateway filter factory. Two reasons: this is
 * the servlet flavour of the gateway, so the reactive AbstractGatewayFilterFactory does not exist
 * here; and a per-route filter could not cover /actuator/**, which matters because the header
 * stripping below has to apply to every request without exception.
 *
 * Runs at HIGHEST_PRECEDENCE so it wraps outside the gateway's own servlet filters (order 9901 and
 * 10001) and rejects unauthenticated requests before any body buffering happens.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final List<PathPattern> publicPatterns;

    /**
     * Patterns are parsed once here rather than per request. PathPatternParser is Spring 7's
     * matcher and is already on the classpath; plain equals() could not express /actuator/** and
     * startsWith() would make /api/recommendation/careers-anything public, which is not the sort
     * of mistake to leave at an unauthenticated boundary.
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, GatewayProperties properties) {
        this.jwtUtil = jwtUtil;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.publicPatterns = properties.publicPaths().stream()
                .map(parser::parse)
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Public paths still get wrapped, with no identity at all. That is what strips any
        // X-User-Id / X-User-Role / X-User-Org-Id the client sent: every downstream service trusts
        // those headers blindly and none of them has Spring Security, so forwarding the raw request
        // here would let anyone act as any student -- or as SUPER_ADMIN -- with a single curl.
        // Never replace this with a bare chain.doFilter(request, response).
        if (isPublicPath(request)) {
            chain.doFilter(new GatewayIdentityRequestWrapper(request, identityHeaders(null, null, null)),
                    response);
            return;
        }

        String header = request.getHeader(GatewayConstants.AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(GatewayConstants.BEARER_PREFIX)) {
            unauthorized(response, GatewayConstants.ERROR_MISSING_TOKEN);
            return;
        }

        String token = header.substring(GatewayConstants.BEARER_PREFIX.length());

        Long userId;
        String role;
        Long orgId;
        try {
            Claims claims = jwtUtil.validateToken(token);
            userId = jwtUtil.extractUserId(claims);
            role = jwtUtil.extractRole(claims);
            orgId = jwtUtil.extractOrgId(claims);
        } catch (ExpiredJwtException ex) {
            // Distinguished from the generic case on purpose: the frontend uses this to decide
            // whether to call /api/auth/refresh rather than bouncing the user to the login screen.
            unauthorized(response, GatewayConstants.ERROR_EXPIRED_TOKEN);
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            // Tampered signature, wrong secret, malformed token, empty string. The reason is
            // logged but never returned -- telling an unauthenticated caller which part failed
            // helps only the caller.
            log.warn("Rejected token for {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
            unauthorized(response, GatewayConstants.ERROR_INVALID_TOKEN);
            return;
        }

        if (userId == null) {
            log.warn("Token for {} carries no {} claim", request.getRequestURI(),
                    GatewayConstants.USER_ID_CLAIM);
            unauthorized(response, GatewayConstants.ERROR_INVALID_TOKEN);
            return;
        }

        // A missing role is NOT rejected here. Every token auth-service issues carries one, but a
        // downstream service that needs a role must decide for itself what an absent header means;
        // failing the request at the gateway would also break the services that never read it.
        // orgId is legitimately null (SUPER_ADMIN, or any user with no organization) and is simply
        // forwarded as an absent header.
        chain.doFilter(new GatewayIdentityRequestWrapper(request, identityHeaders(userId, role, orgId)),
                response);
    }

    /**
     * Builds the header map the wrapper presents. Only non-null values are entered: a null value
     * must surface downstream as an ABSENT header rather than an empty string, so that a service
     * reading it with @RequestHeader(required = false) sees null rather than "".
     *
     * Case-insensitive, because the servlet API's header lookups are.
     */
    private static Map<String, String> identityHeaders(Long userId, String role, Long orgId) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (userId != null) {
            headers.put(GatewayConstants.USER_ID_HEADER, String.valueOf(userId));
        }
        if (role != null) {
            headers.put(GatewayConstants.USER_ROLE_HEADER, role);
        }
        if (orgId != null) {
            headers.put(GatewayConstants.USER_ORG_ID_HEADER, String.valueOf(orgId));
        }
        return headers;
    }

    private boolean isPublicPath(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    /**
     * setStatus, not sendError: sendError triggers an ERROR dispatch to BasicErrorController and
     * would return Boot's {timestamp,status,error,path} shape instead of the contract below.
     *
     * The message is always a compile-time constant from GatewayConstants. Nothing derived from
     * the request may appear here -- this is hand-built JSON returned to an unauthenticated
     * caller, so interpolating a token or an exception message would be JSON injection.
     */
    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\",\"status\":401}");
    }

    /**
     * Presents the gateway's identity headers as whatever the gateway decided, hiding anything the
     * client sent.
     *
     * All three header accessors must be overridden. Spring's ServletRequestHeadersAdapter -- which
     * is what turns this request into the headers the gateway proxies onward -- enumerates via
     * getHeaderNames() and then reads getHeaders(name). Overriding only getHeader(String) compiles,
     * looks correct, and is a silent no-op: the header never leaves the gateway.
     *
     * The security property is in MANAGED_HEADERS, not in the injected map: a managed name is
     * removed from the client's request whether or not the gateway has a replacement for it. An
     * earlier version of this class managed only X-User-Id, which meant a client could set
     * X-User-Role freely and organization-service would have believed it.
     */
    private static final class GatewayIdentityRequestWrapper extends HttpServletRequestWrapper {

        /**
         * Headers the gateway owns end to end. Case-insensitive, matching servlet header semantics
         * -- otherwise "x-user-role" would slip past a check written against "X-User-Role".
         *
         * Anything added here is stripped from client input for free; anything NOT here is
         * forwarded verbatim. Add a header to this set at the same moment you start trusting it
         * downstream, never later.
         */
        private static final Set<String> MANAGED_HEADERS = Collections.unmodifiableSet(
                Stream.of(GatewayConstants.USER_ID_HEADER,
                                GatewayConstants.USER_ROLE_HEADER,
                                GatewayConstants.USER_ORG_ID_HEADER)
                        .collect(Collectors.toCollection(
                                () -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))));

        /** Case-insensitive; contains only the headers the gateway actually has a value for. */
        private final Map<String, String> injected;

        private GatewayIdentityRequestWrapper(HttpServletRequest request, Map<String, String> injected) {
            super(request);
            this.injected = injected;
        }

        @Override
        public String getHeader(String name) {
            if (MANAGED_HEADERS.contains(name)) {
                // null when the gateway has no value: the header reads as absent, and the client's
                // own copy is never consulted.
                return injected.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (MANAGED_HEADERS.contains(name)) {
                String value = injected.get(name);
                return (value == null)
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                // Drop every client-supplied copy; ours are appended below where we have them.
                if (!MANAGED_HEADERS.contains(name)) {
                    names.add(name);
                }
            }
            names.addAll(injected.keySet());
            return Collections.enumeration(names);
        }
    }
}

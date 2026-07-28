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

        // Public paths still get wrapped, with a null id. That is what strips any X-User-Id the
        // client sent: every downstream service trusts that header blindly and has no Spring
        // Security, so forwarding the raw request here would let anyone act as any student with a
        // single curl. Never replace this with a bare chain.doFilter(request, response).
        if (isPublicPath(request)) {
            chain.doFilter(new UserIdRequestWrapper(request, null), response);
            return;
        }

        String header = request.getHeader(GatewayConstants.AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(GatewayConstants.BEARER_PREFIX)) {
            unauthorized(response, GatewayConstants.ERROR_MISSING_TOKEN);
            return;
        }

        String token = header.substring(GatewayConstants.BEARER_PREFIX.length());

        Long userId;
        try {
            Claims claims = jwtUtil.validateToken(token);
            userId = jwtUtil.extractUserId(claims);
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

        chain.doFilter(new UserIdRequestWrapper(request, String.valueOf(userId)), response);
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
     * Presents X-User-Id as whatever the gateway decided, hiding anything the client sent.
     *
     * All three header accessors must be overridden. Spring's ServletRequestHeadersAdapter -- which
     * is what turns this request into the headers the gateway proxies onward -- enumerates via
     * getHeaderNames() and then reads getHeaders(name). Overriding only getHeader(String) compiles,
     * looks correct, and is a silent no-op: the header never leaves the gateway.
     *
     * A null userId means "no identity": the header is then reported absent rather than forwarded
     * from the client.
     */
    private static final class UserIdRequestWrapper extends HttpServletRequestWrapper {

        private final String userId;

        private UserIdRequestWrapper(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if (GatewayConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (GatewayConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return (userId == null)
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(userId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                // Drop the client's own copy; ours is appended below when we have one.
                if (!GatewayConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            if (userId != null) {
                names.add(GatewayConstants.USER_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}

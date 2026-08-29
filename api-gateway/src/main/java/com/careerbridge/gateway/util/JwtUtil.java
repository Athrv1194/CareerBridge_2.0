package com.careerbridge.gateway.util;

import com.careerbridge.gateway.config.GatewayProperties;
import com.careerbridge.gateway.constants.GatewayConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// Mirrors auth-service's JwtConfig exactly. Key derivation, parser API, and claim names must stay in sync.
@Component
public class JwtUtil {

    private final SecretKey key;

    // Keys.hmacShaKeyFor picks HMAC variant from key length -- 49-byte secret = HS384, not HS256.
    // Never name an algorithm explicitly: hardcoding one breaks agreement with auth-service silently.
    // WeakKeyException on startup (< 32 bytes) is deliberate: a gateway that can't verify tokens must not accept traffic.
    public JwtUtil(GatewayProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    // No isTokenExpired() helper -- parseSignedClaims already throws ExpiredJwtException.
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // claims.get(name, Long.class), never a cast -- auth-service writes Long but JSON deserializes
    // small numbers as Integer, so (Long) cast throws ClassCastException for every real user id.
    public Long extractUserId(Claims claims) {
        return claims.get(GatewayConstants.USER_ID_CLAIM, Long.class);
    }

    public String extractRole(Claims claims) {
        return claims.get(GatewayConstants.ROLE_CLAIM, String.class);
    }

    // Same Integer→Long widening trap as extractUserId. Null is normal: SUPER_ADMIN has no org.
    public Long extractOrgId(Claims claims) {
        return claims.get(GatewayConstants.ORG_ID_CLAIM, Long.class);
    }

    // Null for tokens minted before the plan claim existed, or User.subscriptionPlan == null.
    public String extractPlan(Claims claims) {
        return claims.get(GatewayConstants.PLAN_CLAIM, String.class);
    }
}

package com.careerbridge.gateway.util;

import com.careerbridge.gateway.config.GatewayProperties;
import com.careerbridge.gateway.constants.GatewayConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies the access tokens auth-service signs. This is the only place in the whole system where
 * a JWT signature is checked.
 *
 * Mirrors com.careerbridge.auth.config.JwtConfig exactly: same key derivation, same parser API,
 * same claim names. Any divergence here rejects every token, so the two must be changed together.
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    /**
     * Keys.hmacShaKeyFor picks the HMAC variant from the key length, and auth-service signs with
     * the no-argument signWith(key) so it does the same. The shared 49-byte secret is 392 bits,
     * which lands in jjwt's [384, 512) band, so both sides use HS384 -- NOT HS256, despite the
     * secret literal saying "256-bit". Nothing here should ever name an algorithm explicitly:
     * hardcoding one is how the two sides silently stop agreeing.
     *
     * A secret shorter than 32 bytes makes this constructor throw WeakKeyException and the
     * application refuses to start. That is deliberate: a gateway that cannot verify signatures
     * must not accept traffic. Same posture as auth-service.
     */
    public JwtUtil(GatewayProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the token's claims if the signature is valid and it has not expired.
     * @throws io.jsonwebtoken.ExpiredJwtException   token is past its exp
     * @throws io.jsonwebtoken.security.SignatureException  signature does not verify (tampered, or
     *                                               signed with a different secret)
     * @throws io.jsonwebtoken.MalformedJwtException  not a well-formed JWS
     *
     * There is deliberately no separate isTokenExpired() helper: parseSignedClaims already throws
     * ExpiredJwtException, so a second check would mean parsing twice to learn what the first
     * parse already told us.
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Reads the numeric user id that downstream services receive as X-User-Id.
     *
     * claims.get(name, Long.class), never a cast. auth-service writes this as a Long, but JSON has
     * only "number" -- Jackson deserializes anything under 2^31 back as an Integer, so
     * (Long) claims.get("userId") throws ClassCastException for every user whose id fits in an int,
     * which in practice is all of them. jjwt's typed accessor widens Integer to Long for us.
     *
     * @return the id, or null when the claim is absent -- the caller treats that as a 401 rather
     *         than forwarding a request with no identity.
     */
    public Long extractUserId(Claims claims) {
        return claims.get(GatewayConstants.USER_ID_CLAIM, Long.class);
    }
}

package com.careerbridge.gateway;

import com.careerbridge.gateway.config.GatewayProperties;
import com.careerbridge.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tokens here are minted exactly the way auth-service does -- Keys.hmacShaKeyFor over the shared
 * secret, then signWith(key) with no explicit algorithm. If that ever diverges from
 * com.careerbridge.auth.config.JwtConfig, these tests stop being evidence of anything.
 */
class JwtUtilTest {

    /** Byte-identical to auth-service's jwt.secret and the gateway's default. 49 bytes. */
    private static final String SECRET = "your-256-bit-secret-key-here-change-in-production";
    private static final String OTHER_SECRET = "a-completely-different-secret-that-is-long-enough-xx";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(new GatewayProperties(SECRET, List.of()));
    }

    private static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String secret, Object userIdClaim, long ttlMillis) {
        var builder = Jwts.builder()
                .subject("ada@careerbridge.com")
                .claim("role", "STUDENT")
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + ttlMillis));
        if (userIdClaim != null) {
            builder.claim("userId", userIdClaim);
        }
        return builder.signWith(keyFor(secret)).compact();
    }

    @Test
    @DisplayName("valid token: signature verifies and the claims come back intact")
    void generateAndValidate_ValidToken_ReturnsCorrectClaims() {
        Claims claims = jwtUtil.validateToken(token(SECRET, 42L, 900_000));

        assertEquals("ada@careerbridge.com", claims.getSubject());
        assertEquals("STUDENT", claims.get("role", String.class));
        assertEquals(42L, jwtUtil.extractUserId(claims));
    }

    @Test
    @DisplayName("HS384, not HS256: jjwt picks the variant from the 49-byte key length")
    void signedToken_UsesHS384_NotHS256() {
        // auth-service calls signWith(key) with no algorithm argument, so the key length decides.
        // 49 bytes = 392 bits, which lands in jjwt's [384, 512) band. Nothing in this project may
        // hardcode an algorithm -- that is how signer and verifier silently stop agreeing.
        String alg = Jwts.parser()
                .verifyWith(keyFor(SECRET))
                .build()
                .parseSignedClaims(token(SECRET, 42L, 900_000))
                .getHeader()
                .getAlgorithm();

        assertEquals("HS384", alg);
        assertEquals("HmacSHA384", keyFor(SECRET).getAlgorithm());
    }

    @Test
    @DisplayName("expired token: throws ExpiredJwtException so the filter can say so specifically")
    void validateToken_ExpiredToken_ThrowsException() {
        String expired = token(SECRET, 42L, -60_000);

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(expired));
    }

    @Test
    @DisplayName("tampered token: a real byte change is rejected, unlike a padding-only edit")
    void validateToken_TamperedToken_ThrowsException() {
        String valid = token(SECRET, 42L, 900_000);
        String[] parts = valid.split("\\.");

        // Genuine tamper: flip a character in the payload, which changes what was signed.
        // NOTE: appending a character to the signature is NOT a valid tamper test -- a 48-byte
        // HS384 signature is exactly 64 base64url chars, so a 65th char contributes 6 bits that
        // decode to the same bytes and the token still verifies. That mistake produces a test
        // that passes for the wrong reason.
        String tamperedPayload = parts[0] + "." + ("Z" + parts[1].substring(1)) + "." + parts[2];
        assertThrows(SignatureException.class, () -> jwtUtil.validateToken(tamperedPayload));

        // Second genuine tamper: graft on a signature produced with a different key.
        String foreignSignature = token(OTHER_SECRET, 42L, 900_000).split("\\.")[2];
        String grafted = parts[0] + "." + parts[1] + "." + foreignSignature;
        assertThrows(SignatureException.class, () -> jwtUtil.validateToken(grafted));
    }

    @Test
    @DisplayName("wrong secret: a token auth-service did not sign is rejected")
    void validateToken_WrongSecret_ThrowsException() {
        String foreign = token(OTHER_SECRET, 42L, 900_000);

        assertThrows(SignatureException.class, () -> jwtUtil.validateToken(foreign));
    }

    @Test
    @DisplayName("malformed token: not a JWS at all")
    void validateToken_MalformedToken_ThrowsException() {
        assertThrows(MalformedJwtException.class, () -> jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    @DisplayName("userId claim: a small number deserializes as Integer, so a cast would blow up")
    void extractUserId_ValidClaims_ReturnsLong() {
        Claims claims = jwtUtil.validateToken(token(SECRET, 42L, 900_000));

        // auth-service writes this as a Long, but JSON has only "number" -- Jackson hands back an
        // Integer for anything under 2^31, which is every real user id. (Long) claims.get("userId")
        // would throw ClassCastException; the typed accessor widens instead.
        assertEquals(Integer.class, claims.get("userId").getClass());
        assertThrows(ClassCastException.class, () -> {
            Long ignored = (Long) claims.get("userId");
        });

        Long userId = jwtUtil.extractUserId(claims);
        assertEquals(Long.class, userId.getClass());
        assertEquals(42L, userId);
    }

    @Test
    @DisplayName("userId claim missing: returns null so the filter can 401 rather than forward")
    void extractUserId_MissingClaim_ReturnsNull() {
        Claims claims = jwtUtil.validateToken(token(SECRET, null, 900_000));

        assertNull(jwtUtil.extractUserId(claims));
    }

    @Test
    @DisplayName("weak secret: the gateway refuses to start rather than accept unverifiable traffic")
    void constructor_SecretTooShort_ThrowsWeakKeyException() {
        GatewayProperties weak = new GatewayProperties("too-short", List.of());

        assertThrows(WeakKeyException.class, () -> new JwtUtil(weak));
    }
}

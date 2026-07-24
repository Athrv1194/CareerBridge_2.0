package com.careerbridge.auth.config;

import com.careerbridge.auth.constants.JwtConstants;
import com.careerbridge.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtConfig {

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtConfig(@Value("${jwt.secret}") String secret,
                     @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
                     @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        // hmacShaKeyFor rejects anything under 256 bits, so a too-short jwt.secret
        // fails loudly at startup rather than silently weakening every token.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(JwtConstants.USER_ID_CLAIM, user.getId())
                .claim(JwtConstants.ROLES_CLAIM, user.getRole().name())
                .claim(JwtConstants.ORG_ID_CLAIM, user.getOrganizationId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiry))
                .signWith(key)
                .compact();
    }

    /** Opaque random token — validity lives in the refresh_tokens table, not in the string. */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public LocalDateTime getRefreshTokenExpiryDate() {
        return LocalDateTime.now().plusNanos(refreshTokenExpiry * 1_000_000);
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with, or expired. */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return validateToken(token).getSubject();
    }
}

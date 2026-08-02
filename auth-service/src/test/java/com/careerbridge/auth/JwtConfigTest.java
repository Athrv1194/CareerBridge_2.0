package com.careerbridge.auth;

import com.careerbridge.auth.config.JwtConfig;
import com.careerbridge.auth.constants.JwtConstants;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A real JwtConfig, not a mock: AuthServiceTest mocks it, so the claim set itself is only
 * observable here. The secret is 49 characters, matching the deployed one -- jjwt's
 * Keys.hmacShaKeyFor picks the strongest HMAC the key length allows, so 49 bytes selects HS384,
 * and a shorter secret here would silently exercise a different algorithm than production.
 */
class JwtConfigTest {

    private static final String SECRET = "your-256-bit-secret-key-here-change-in-production";

    private final JwtConfig jwtConfig = new JwtConfig(SECRET, 900_000L, 604_800_000L);

    private static User user(String plan) {
        return User.builder()
                .id(21L).email("s@test.com").firstName("S").lastName("T")
                .role(Role.STUDENT).organizationId(7L).subscriptionPlan(plan)
                .isDeleted(false).build();
    }

    @Test
    @DisplayName("The access token carries the subscription plan so the gateway can inject X-User-Plan")
    void generateAccessToken_CarriesPlanClaim() {
        Claims claims = jwtConfig.validateToken(jwtConfig.generateAccessToken(user("STUDENT_PREMIUM")));

        assertEquals("STUDENT_PREMIUM", claims.get(JwtConstants.PLAN_CLAIM, String.class));
    }

    @Test
    void generateAccessToken_StillCarriesTheThreeOriginalClaims() {
        // The plan claim is additive. Breaking any of these would break every downstream service.
        Claims claims = jwtConfig.validateToken(jwtConfig.generateAccessToken(user("FREE")));

        assertEquals(21L, claims.get(JwtConstants.USER_ID_CLAIM, Long.class));
        assertEquals("STUDENT", claims.get(JwtConstants.ROLES_CLAIM, String.class));
        assertEquals(7L, claims.get(JwtConstants.ORG_ID_CLAIM, Long.class));
        assertEquals("s@test.com", claims.getSubject());
    }

    @Test
    @DisplayName("A null plan omits the claim rather than emitting an empty string")
    void generateAccessToken_NullPlan_OmitsPlanClaim() {
        // subscriptionPlan has no NOT NULL constraint, so a row written before the @Builder.Default
        // existed can legitimately be null. An absent claim makes the gateway forward no header at
        // all, which is what a downstream @RequestHeader(required = false) expects -- an empty
        // string would look like a real plan named "".
        Claims claims = jwtConfig.validateToken(jwtConfig.generateAccessToken(user(null)));

        assertNull(claims.get(JwtConstants.PLAN_CLAIM, String.class));
    }

    @Test
    void generateAccessToken_FreePlan_CarriesTheLiteralFree() {
        // "FREE" is the literal User.subscriptionPlan has defaulted to since the project started,
        // and the payment-service catalog row was named to match it rather than the other way round.
        Claims claims = jwtConfig.validateToken(jwtConfig.generateAccessToken(user("FREE")));

        assertEquals("FREE", claims.get(JwtConstants.PLAN_CLAIM, String.class));
    }
}

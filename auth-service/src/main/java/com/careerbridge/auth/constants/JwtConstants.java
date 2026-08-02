package com.careerbridge.auth.constants;

public class JwtConstants {

    public static final String SECRET_KEY = "jwt.secret";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_STRING = "Authorization";
    public static final String ROLES_CLAIM = "role";
    public static final String USER_ID_CLAIM = "userId";
    public static final String ORG_ID_CLAIM = "organizationId";

    /**
     * The subscription plan, carried so api-gateway can inject X-User-Plan downstream.
     *
     * Adding a field to the User entity does NOT put it in the token -- generateAccessToken writes
     * an explicit list of .claim(...) calls, so a new claim needs an entry there too. Nothing
     * enforces premium on this claim yet; it is advisory. The authoritative answer is
     * GET /api/payment/subscription/my, which reads payment-service's own rows and is immediately
     * consistent, whereas this claim lags by the RabbitMQ delivery plus the access-token lifetime.
     */
    public static final String PLAN_CLAIM = "plan";
}

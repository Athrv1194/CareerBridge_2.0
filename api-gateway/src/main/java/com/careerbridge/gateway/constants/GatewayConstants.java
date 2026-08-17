package com.careerbridge.gateway.constants;

public class GatewayConstants {

    // Gateway strips and re-injects all four headers on every request -- client-supplied values never reach downstream
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    // Legitimately absent for SUPER_ADMIN (no owning org). Absent = no org, never = any org.
    public static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    // Managed but not yet enforced by any service. Must stay in MANAGED_HEADERS -- a header the
    // gateway forwards without overwriting lets any caller self-grant premium for free.
    // Absent for tokens minted before the claim existed. Advisory: lags by token TTL.
    public static final String USER_PLAN_HEADER = "X-User-Plan";

    /** Must match auth-service's JwtConstants.HEADER_STRING. */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Must match auth-service's JwtConstants.TOKEN_PREFIX, trailing space included. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Must match auth-service's JwtConstants.USER_ID_CLAIM. */
    public static final String USER_ID_CLAIM = "userId";

    // Singular "role" despite the constant's plural name in auth-service -- one role per token.
    /** Must match auth-service's JwtConstants.ROLES_CLAIM. */
    public static final String ROLE_CLAIM = "role";

    /** Must match auth-service's JwtConstants.ORG_ID_CLAIM. */
    public static final String ORG_ID_CLAIM = "organizationId";

    /** Must match auth-service's JwtConstants.PLAN_CLAIM. */
    public static final String PLAN_CLAIM = "plan";

    // Literal strings only -- interpolating request-derived values here is JSON injection at an unauthenticated boundary.
    public static final String ERROR_MISSING_TOKEN = "Missing or malformed Authorization header";
    public static final String ERROR_INVALID_TOKEN = "Invalid token";
    public static final String ERROR_EXPIRED_TOKEN = "Token expired";

    private GatewayConstants() {
    }
}

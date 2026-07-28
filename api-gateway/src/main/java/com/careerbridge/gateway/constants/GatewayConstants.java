package com.careerbridge.gateway.constants;

public class GatewayConstants {

    /**
     * The header every downstream service reads to identify the caller.
     *
     * This gateway is the ONLY component that validates a JWT; auth/student/assessment/
     * recommendation/notification all trust this header without question and none of them has
     * Spring Security. Two consequences: the filter must overwrite any client-supplied value on
     * every request (a caller could otherwise set it directly and act as any student), and ports
     * 8081-8085 must not be publicly reachable at deploy time.
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Must match auth-service's JwtConstants.HEADER_STRING. */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Must match auth-service's JwtConstants.TOKEN_PREFIX, trailing space included. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Claim carrying the numeric user id. Must match auth-service's JwtConstants.USER_ID_CLAIM. */
    public static final String USER_ID_CLAIM = "userId";

    /**
     * 401 bodies, written directly by the filter.
     *
     * Compile-time constants only. These are interpolated into hand-built JSON, so anything
     * derived from the request -- the token, a JwtException message -- would be JSON injection at
     * an unauthenticated trust boundary. Keep them literal.
     */
    public static final String ERROR_MISSING_TOKEN = "Missing or malformed Authorization header";
    public static final String ERROR_INVALID_TOKEN = "Invalid token";
    public static final String ERROR_EXPIRED_TOKEN = "Token expired";

    private GatewayConstants() {
    }
}

package com.careerbridge.payment.constants;

import java.util.Set;

/**
 * Service-scoped constants, private ctor, no instantiation -- same convention as JwtConstants
 * (auth-service) and AiCoachConstants (ai-coach-service).
 */
public final class PaymentConstants {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ORG_ADMIN = "ORG_ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /** Who may buy a subscription. A RECRUITER has nothing to subscribe to. */
    public static final Set<String> SUBSCRIBER_ROLES = Set.of(ROLE_STUDENT, ROLE_ORG_ADMIN);

    /** Who may read the whole subscription roster. */
    public static final Set<String> ADMIN_ROLES = Set.of(ROLE_ORG_ADMIN, ROLE_SUPER_ADMIN);

    public static final String CURRENCY_INR = "INR";

    /** Razorpay caps the receipt field at 40 characters. */
    public static final int RECEIPT_MAX_LENGTH = 40;

    private PaymentConstants() {
    }
}

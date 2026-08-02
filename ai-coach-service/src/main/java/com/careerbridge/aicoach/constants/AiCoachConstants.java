package com.careerbridge.aicoach.constants;

/**
 * Service-scoped constants, private ctor, no instantiation -- same convention as JwtConstants
 * (auth-service) and SkillConstants (student-service).
 */
public final class AiCoachConstants {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /** Chat history sent to Groq is trimmed to the last N messages, bounding prompt token cost. */
    public static final int MAX_HISTORY = 20;

    /** Chat request body cap -- without this, one pasted document burns the Groq quota in one call. */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    /** Skills interpolated into the system prompt are capped, same reasoning as MAX_MESSAGE_LENGTH. */
    public static final int MAX_SKILLS_IN_PROMPT = 15;

    private AiCoachConstants() {
    }
}

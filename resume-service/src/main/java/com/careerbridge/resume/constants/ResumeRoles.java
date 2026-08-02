package com.careerbridge.resume.constants;

/**
 * The X-User-Role values this service authorizes on. Strings, not auth-service's Role enum: the
 * value arrives as a header, and a duplicated enum would make every request hard-fail the day
 * auth-service adds a seventh role. Same reasoning as recruiter-service's RecruiterRoles.
 *
 * Private constructor, no instantiation -- matching auth-service's JwtConstants and
 * student-service's SkillConstants.
 */
public final class ResumeRoles {

    public static final String STUDENT = "STUDENT";
    public static final String RECRUITER = "RECRUITER";
    public static final String PLACEMENT_OFFICER = "PLACEMENT_OFFICER";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private ResumeRoles() {
    }
}

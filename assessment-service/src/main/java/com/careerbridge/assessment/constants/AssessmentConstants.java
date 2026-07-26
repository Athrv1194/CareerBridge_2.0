package com.careerbridge.assessment.constants;

public class AssessmentConstants {

    /**
     * Highest weight any single option carries. Doubles as the per-question ceiling used to derive
     * maxPossibleScore, so seed data must never exceed it -- an option weighted above this silently
     * produces scores over 100%.
     */
    public static final int MAX_OPTION_WEIGHT = 3;

    /** A category with fewer questions than this cannot be started; enforced in startAttempt. */
    public static final int MIN_QUESTIONS_PER_CATEGORY = 5;

    /**
     * How many questions each attempt draws from the category, at random.
     *
     * Equal to the category minimum, so every startable category can fill an attempt. This is also
     * the score denominator (x MAX_OPTION_WEIGHT) and the cap on how many answers a submit accepts
     * -- change it and both move together.
     */
    public static final int QUESTIONS_PER_ATTEMPT = MIN_QUESTIONS_PER_CATEGORY;

    public static final int TOP_CAREERS_TO_RECOMMEND = 3;

    /** Must match the exchange auth-service and student-service already declare. */
    public static final String EXCHANGE_NAME = "careerbridge.exchange";

    public static final String ROUTING_KEY_ASSESSMENT_COMPLETED = "assessment.completed";

    private AssessmentConstants() {
    }
}

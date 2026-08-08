package com.careerbridge.assessment.constants;

import java.util.List;
import java.util.Map;

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
     * Which real Category row(s) can back each fixed section. startAttempt picks one at random from
     * the section's pool every time, so a retake can genuinely land on a different underlying
     * category -- the client only ever sees the section's display name, never which pool member won.
     * Aptitude and Soft Skills have no pre-existing content to draw from, so their pools are a single
     * category; Domain Knowledge deliberately reuses the two pre-existing programming-topic
     * categories alongside its own, so a retake has real variety instead of showing the same 10
     * questions every time.
     */
    public static final Map<AssessmentSection, List<String>> SECTION_CATEGORY_POOL = Map.of(
            AssessmentSection.APTITUDE, List.of("Aptitude"),
            AssessmentSection.DOMAIN_KNOWLEDGE,
            List.of("Domain Knowledge", "Programming Fundamentals", "Database & SQL"),
            AssessmentSection.SOFT_SKILLS, List.of("Soft Skills"));

    public static final int TOP_CAREERS_TO_RECOMMEND = 3;

    /** Must match the exchange auth-service and student-service already declare. */
    public static final String EXCHANGE_NAME = "careerbridge.exchange";

    public static final String ROUTING_KEY_ASSESSMENT_COMPLETED = "assessment.completed";

    private AssessmentConstants() {
    }
}

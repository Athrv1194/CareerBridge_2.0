package com.careerbridge.recommendation.constants;

public class RecommendationConstants {

    /** Must match the exchange auth-service, student-service and assessment-service already declare. */
    public static final String EXCHANGE_NAME = "careerbridge.exchange";

    /** Literal must equal AssessmentConstants.ROUTING_KEY_ASSESSMENT_COMPLETED in assessment-service. */
    public static final String ROUTING_KEY_ASSESSMENT_COMPLETED = "assessment.completed";

    public static final String ROUTING_KEY_RECOMMENDATION_GENERATED = "recommendation.generated";

    /** This service owns this queue; the publisher declares only the exchange. */
    public static final String QUEUE_NAME = "careerbridge.recommendation.queue";

    /**
     * Rank cutoff splitting RecommendationResponse into topRecommendations / otherCareers, and the
     * sole input to CareerRanking.isTopRecommendation. Matches
     * AssessmentConstants.TOP_CAREERS_TO_RECOMMEND so both services agree on what "top" means.
     *
     * There is deliberately no score threshold beside it. Only two assessment categories currently
     * have seeded questions and neither substring-matches any career's requiredSkills, so every real
     * event scores all seven careers at 0.3 relevance -- a ceiling of 30.0. Any minimum-match gate
     * above that empties topRecommendations on every assessment, and any gate below it expires the
     * day a matching category gets seeded and scores jump to 100. "Top" is a rank concept; how good
     * the match is already reaches the student through RecommendationEngine's 80/60/40 reason tiers.
     */
    public static final int TOP_CAREERS_COUNT = 3;

    private RecommendationConstants() {
    }
}

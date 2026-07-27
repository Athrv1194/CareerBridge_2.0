package com.careerbridge.recommendation.util;

import java.util.Locale;

/**
 * Pure recommendation arithmetic and prose -- no repositories, no Spring. Every method is static
 * and side-effect free, which is why it is unit-tested directly rather than only through the
 * service.
 */
public class RecommendationEngine {

    /**
     * Local mirror of assessment-service's ScoringEngine.calculateCareerMatches, reduced to one
     * career. Kept byte-identical on purpose: this service recomputes the full ranking that the
     * event does not carry, and if the two ever disagree, rank 1 here would contradict the
     * topCareerPath the student was already shown.
     *
     * Relevance is a crude substring test -- a career whose requiredSkills mentions the category
     * name scores at full weight, everything else at 0.3. Naive by design; it is assessment-service's
     * heuristic, reproduced rather than improved, so the two stay comparable. Change one and the
     * other must change with it.
     *
     * Precondition: categoryName is non-null. The consumer guards this before calling; a null
     * requiredSkills is fine and simply scores as irrelevant.
     */
    public static double calculateMatchScore(String categoryName,
                                             double categoryScorePercentage,
                                             String requiredSkills) {
        double relevanceWeight = requiredSkills != null
                && requiredSkills.toLowerCase(Locale.ROOT)
                        .contains(categoryName.toLowerCase(Locale.ROOT)) ? 1.0 : 0.3;
        return Math.round((categoryScorePercentage * relevanceWeight) * 100.0) / 100.0;
    }

    // Generate reason text for a career ranking
    public static String generateCareerReasonText(
            String careerName,
            String categoryName,
            double matchPercentage) {
        if (matchPercentage >= 80) {
            return String.format(Locale.ROOT,
                "Excellent match! Your %s score of %.1f%% makes you a strong candidate for %s. You demonstrate solid foundational knowledge in this domain.",
                categoryName, matchPercentage, careerName);
        } else if (matchPercentage >= 60) {
            return String.format(Locale.ROOT,
                "Good match. Your %s score of %.1f%% shows you have the core skills for %s. With focused practice, you can excel in this career path.",
                categoryName, matchPercentage, careerName);
        } else if (matchPercentage >= 40) {
            return String.format(Locale.ROOT,
                "Moderate match. Your %s score of %.1f%% indicates potential for %s. Consider strengthening your fundamentals to improve your fit.",
                categoryName, matchPercentage, careerName);
        } else {
            return String.format(Locale.ROOT,
                "Exploratory match. %s is an interesting career to consider alongside your primary recommendations. Building skills in %s will open this path.",
                careerName, categoryName);
        }
    }

    // Generate strength area text
    public static String generateStrengthText(String categoryName, double scorePercentage) {
        if (scorePercentage >= 70) {
            return String.format(Locale.ROOT,
                "Strong understanding of %s concepts (%.1f%% score)", categoryName, scorePercentage);
        } else {
            return String.format(Locale.ROOT,
                "Foundational knowledge of %s (%.1f%% score)", categoryName, scorePercentage);
        }
    }

    // Generate improvement area text
    public static String generateImprovementText(String categoryName, double scorePercentage) {
        if (scorePercentage < 50) {
            return String.format(Locale.ROOT,
                "Core %s concepts need significant strengthening", categoryName);
        } else if (scorePercentage < 70) {
            return String.format(Locale.ROOT,
                "Intermediate %s topics -- practice more complex problems", categoryName);
        } else {
            return String.format(Locale.ROOT,
                "Advanced %s topics -- explore system design and architecture", categoryName);
        }
    }

    // Generate actionable advice
    public static String generateActionableAdvice(String topCareerName, double matchPercentage) {
        if (matchPercentage >= 70) {
            return String.format(Locale.ROOT,
                "You are well-positioned for %s. Start building real projects, contribute to open source, and prepare for technical interviews.",
                topCareerName);
        } else if (matchPercentage >= 50) {
            return String.format(Locale.ROOT,
                "To strengthen your path to %s: complete your learning roadmap, build 2-3 portfolio projects, and retake the assessment after 30 days of practice.",
                topCareerName);
        } else {
            return String.format(Locale.ROOT,
                "Focus on strengthening your fundamentals first. Complete online courses in your weak areas, then revisit %s as a career goal in 60-90 days.",
                topCareerName);
        }
    }

    private RecommendationEngine() {
    }
}

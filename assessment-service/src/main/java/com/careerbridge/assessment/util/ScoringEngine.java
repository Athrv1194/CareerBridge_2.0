package com.careerbridge.assessment.util;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.model.AttemptAnswer;
import com.careerbridge.assessment.model.CareerPath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure scoring arithmetic -- no repositories, no Spring. Every method is static and side-effect
 * free, which is why it is unit-tested directly rather than only through the service.
 */
public class ScoringEngine {

    /** Step 1: sum of the weights actually earned. Unanswered questions simply contribute nothing. */
    public static int calculateRawScore(List<AttemptAnswer> answers) {
        return answers.stream()
                .mapToInt(AttemptAnswer::getWeightEarned)
                .sum();
    }

    /**
     * Step 2: ceiling for the whole category, not for the answers submitted.
     * The caller passes the category's question count so the denominator stays a server-side fact --
     * deriving it from the payload would let one perfect answer out of twenty score 100%.
     */
    public static int calculateMaxPossibleScore(int questionCount) {
        return questionCount * AssessmentConstants.MAX_OPTION_WEIGHT;
    }

    /** Step 3: percentage, rounded to two decimals. Guards the empty-category divide-by-zero. */
    public static double calculateCategoryScorePercentage(int rawScore, int maxPossibleScore) {
        if (maxPossibleScore == 0) {
            return 0.0;
        }
        return Math.round((rawScore * 100.0 / maxPossibleScore) * 100.0) / 100.0;
    }

    /**
     * Step 4: score every career against this category's result.
     *
     * Relevance is a crude substring test -- a career whose requiredSkills mentions the category
     * name scores at full weight, everything else at 0.3. Deliberately naive: it is a heuristic
     * placeholder until recommendation-service owns real matching.
     */
    public static Map<String, Double> calculateCareerMatches(String categoryName,
                                                             double categoryScorePercentage,
                                                             List<CareerPath> allCareers) {
        Map<String, Double> careerScores = new LinkedHashMap<>();
        for (CareerPath career : allCareers) {
            double relevanceWeight = career.getRequiredSkills() != null
                    && career.getRequiredSkills().toLowerCase()
                            .contains(categoryName.toLowerCase()) ? 1.0 : 0.3;
            double matchScore = Math.round(
                    (categoryScorePercentage * relevanceWeight) * 100.0) / 100.0;
            careerScores.put(career.getName(), matchScore);
        }
        return careerScores;
    }

    /** Step 5: highest-scoring N, insertion-ordered so the caller can read the winner off the front. */
    public static Map<String, Double> getTopCareers(Map<String, Double> careerScores, int topN) {
        return careerScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    private ScoringEngine() {
    }
}

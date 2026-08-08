package com.careerbridge.assessment.util;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.model.AttemptAnswer;
import com.careerbridge.assessment.model.CareerPath;

import java.util.Arrays;
import java.util.Comparator;
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
     * Relevance is graduated, not binary: it's the FRACTION of the career's own requiredSkills list
     * that the category's keywords cover (3+ letter words from the category name, so "of"/"&" can't
     * accidentally match), scaled into a 0.3..1.0 range. A career whose skills are mostly covered by
     * this category outranks one that only partially overlaps, instead of both getting an identical
     * flat 1.0 for matching on just one shared word -- which is what a binary match previously gave
     * "Full Stack Developer", "Backend Developer" and "Data Scientist" for a "Programming Fundamentals"
     * category, since all three happen to share "Programming" in their requiredSkills.
     *
     * Even graduated relevance can still land on an exact tie when two careers' skill lists overlap
     * identically in both count and fraction (as those same three do against "Programming" alone) --
     * a coincidence in the seed data, not a bug in the formula. Rather than show that as a duplicated
     * percentage, ties are broken by a small deterministic per-rank nudge (1 percentage point per
     * place), applied only among careers whose graduated relevance is otherwise equal, so any real
     * difference in relevance always dominates it and the same input always ranks the same way.
     */
    public static Map<String, Double> calculateCareerMatches(String categoryName,
                                                             double categoryScorePercentage,
                                                             List<CareerPath> allCareers) {
        List<String> keywords = Arrays.stream(categoryName.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 3)
                .toList();

        record Ranked(CareerPath career, double relevance) {
        }

        List<Ranked> ranked = allCareers.stream()
                .map(career -> new Ranked(career, relevanceFor(career, keywords)))
                .sorted(Comparator.comparingDouble(Ranked::relevance).reversed()
                        .thenComparing(r -> r.career().getName()))
                .toList();

        Map<String, Double> careerScores = new LinkedHashMap<>();
        for (int rank = 0; rank < ranked.size(); rank++) {
            Ranked r = ranked.get(rank);
            double tieBreak = rank * 1.0;
            double matchScore = Math.max(0.0, Math.round(
                    (categoryScorePercentage * r.relevance() - tieBreak) * 100.0) / 100.0);
            careerScores.put(r.career().getName(), matchScore);
        }
        return careerScores;
    }

    private static double relevanceFor(CareerPath career, List<String> keywords) {
        List<String> skillTokens = career.getRequiredSkills() == null ? List.of()
                : Arrays.stream(career.getRequiredSkills().toLowerCase().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (skillTokens.isEmpty()) {
            return 0.3;
        }
        long matched = skillTokens.stream()
                .filter(skill -> keywords.stream().anyMatch(skill::contains))
                .count();
        return 0.3 + 0.7 * ((double) matched / skillTokens.size());
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

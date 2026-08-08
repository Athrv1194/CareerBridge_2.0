package com.careerbridge.assessment;

import com.careerbridge.assessment.model.AttemptAnswer;
import com.careerbridge.assessment.model.CareerPath;
import com.careerbridge.assessment.util.ScoringEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The only real arithmetic in this service, so it is asserted directly rather than via the service. */
class ScoringEngineTest {

    private static AttemptAnswer answer(int weightEarned) {
        return AttemptAnswer.builder().weightEarned(weightEarned).build();
    }

    private static CareerPath career(String name, String requiredSkills) {
        return CareerPath.builder().name(name).requiredSkills(requiredSkills).build();
    }

    @Test
    @DisplayName("rawScore: five answers at the maximum weight sum to the ceiling")
    void calculateRawScore_AllMaxWeights_ReturnsMax() {
        List<AttemptAnswer> answers = List.of(answer(3), answer(3), answer(3), answer(3), answer(3));

        assertEquals(15, ScoringEngine.calculateRawScore(answers));
        assertEquals(0, ScoringEngine.calculateRawScore(List.of()));
    }

    @Test
    @DisplayName("maxPossibleScore: question count times the per-option ceiling")
    void calculateMaxPossibleScore_MultipliesByMaxWeight() {
        assertEquals(15, ScoringEngine.calculateMaxPossibleScore(5));
        assertEquals(0, ScoringEngine.calculateMaxPossibleScore(0));
    }

    @Test
    @DisplayName("percentage: uneven division rounds to two decimals, and zero max cannot divide by zero")
    void calculateCategoryScorePercentage_CorrectCalculation() {
        assertEquals(100.0, ScoringEngine.calculateCategoryScorePercentage(15, 15));
        assertEquals(46.67, ScoringEngine.calculateCategoryScorePercentage(7, 15));
        assertEquals(0.0, ScoringEngine.calculateCategoryScorePercentage(0, 15));
        // Guard: without it this is a divide-by-zero producing NaN, which Math.round silently
        // flattens to 0 and would hide an empty-category bug.
        assertEquals(0.0, ScoringEngine.calculateCategoryScorePercentage(9, 0));
    }

    @Test
    @DisplayName("career match: relevance is graduated by how much of the career's own skill list "
            + "the category covers, not a flat full weight for any match")
    void calculateCareerMatches_RelevantCareer_GetsFullWeight() {
        // 1 of 2 skill tokens ("logical reasoning") matches -- relevance = 0.3 + 0.7*(1/2) = 0.65.
        List<CareerPath> careers = List.of(career("Data Scientist", "Python, Logical Reasoning"));

        Map<String, Double> scores =
                ScoringEngine.calculateCareerMatches("Logical Reasoning", 80.0, careers);

        assertEquals(52.0, scores.get("Data Scientist"));
        // Matching is case-insensitive, so seed data casing cannot silently downgrade a career.
        assertEquals(52.0,
                ScoringEngine.calculateCareerMatches("logical reasoning", 80.0, careers)
                        .get("Data Scientist"));
    }

    @Test
    @DisplayName("career match: an unrelated career is damped to the 30% floor, null skills do not "
            + "blow up, and careers with ZERO real signal get the identical score, not an "
            + "alphabetical tiebreak")
    void calculateCareerMatches_IrrelevantCareer_GetsPartialWeight() {
        List<CareerPath> careers = List.of(
                career("Chef", "Cooking, Plating"),
                career("Unseeded", null));

        Map<String, Double> scores =
                ScoringEngine.calculateCareerMatches("Logical Reasoning", 80.0, careers);

        // Both are equally irrelevant (0.3 floor) -- the category name shares no keyword with
        // EITHER career, so there is no real signal to rank them by. Applying the per-rank
        // tiebreak here would silently favor whichever name sorts first alphabetically for every
        // student, every time -- the exact bug that made "Backend Developer" the default answer
        // regardless of actual assessment content. Both must score identically instead.
        assertEquals(24.0, scores.get("Chef"));
        assertEquals(24.0, scores.get("Unseeded"));
    }

    @Test
    @DisplayName("career match: when relevance genuinely differs, the tiebreak still applies to "
            + "careers coincidentally tied at the SAME non-floor relevance")
    void calculateCareerMatches_PartialTieAmongRealSignal_StillTiebreaks() {
        // "Python" and "Java" both hit exactly 1 of 2 skill tokens against "Programming" -- a real,
        // non-floor relevance (0.65) shared by two careers -- while a third career is fully
        // irrelevant (0.3 floor). Real signal exists (relevance varies across the set), so the
        // near-tie between Python and Java should still be broken to avoid a duplicate percentage.
        List<CareerPath> careers = List.of(
                career("Python Dev", "Programming, Testing"),
                career("Java Dev", "Programming, Testing"),
                career("Chef", "Cooking, Plating"));

        Map<String, Double> scores =
                ScoringEngine.calculateCareerMatches("Programming", 80.0, careers);

        assertEquals(52.0, scores.get("Java Dev"));
        assertEquals(51.0, scores.get("Python Dev"));
        // Rank 2 (not tied with anyone), but the tiebreak is a per-rank penalty applied across the
        // whole ranking once real signal exists, so it still lands here: 80*0.3 - 2 = 22.0.
        assertEquals(22.0, scores.get("Chef"));
    }

    @Test
    @DisplayName("top careers: returns the highest N in descending order, winner first")
    void getTopCareers_ReturnsSortedByScoreDescending() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("Analyst", 24.0);
        scores.put("Engineer", 80.0);
        scores.put("Designer", 50.0);
        scores.put("Researcher", 91.0);

        Map<String, Double> top = ScoringEngine.getTopCareers(scores, 3);

        assertEquals(3, top.size());
        assertEquals(List.of("Researcher", "Engineer", "Designer"), new ArrayList<>(top.keySet()));
        // The service reads the winner off the front rather than doing a separate max search.
        assertEquals("Researcher", top.entrySet().iterator().next().getKey());
    }

    @Test
    @DisplayName("top careers: asking for more than exist returns all of them, and none returns empty")
    void getTopCareers_FewerCareersThanN_ReturnsAllAvailable() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("Engineer", 80.0);

        assertEquals(1, ScoringEngine.getTopCareers(scores, 3).size());
        // Empty career table: the service must still record a result, with no top career.
        assertEquals(0, ScoringEngine.getTopCareers(new LinkedHashMap<>(), 3).size());
        assertNull(ScoringEngine.getTopCareers(new LinkedHashMap<>(), 3)
                .entrySet().stream().findFirst().orElse(null));
    }
}

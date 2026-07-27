package com.careerbridge.recommendation;

import com.careerbridge.recommendation.util.RecommendationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure static arithmetic and prose -- no Spring, no mocks.
 *
 * Expected values here are recomputed from the inputs, never copied from assessment-service's
 * ScoringEngineTest: the same career can legitimately score 24.0 there and 30.0 here depending on
 * the category percentage under test, and both look plausible on inspection. Transcribing one
 * across already cost a build (ai_incident_log.md, 2026-07-26 15:58).
 */
class RecommendationEngineTest {

    private static final String RELEVANT_CATEGORY = "Web Development";
    private static final String IRRELEVANT_CATEGORY = "Programming Fundamentals";
    private static final String FULL_STACK_SKILLS = "Programming,Database,Web Development";

    @Test
    @DisplayName("match score: a category named in requiredSkills scores at full relevance")
    void calculateMatchScore_CategoryNameInRequiredSkills_ScoresAtFullRelevance() {
        // "web development" is a substring of the skills list -> weight 1.0 -> 100.0 * 1.0
        assertEquals(100.0,
                RecommendationEngine.calculateMatchScore(RELEVANT_CATEGORY, 100.0, FULL_STACK_SKILLS));
    }

    @Test
    @DisplayName("match score: an unrelated category damps to 30 percent of the category score")
    void calculateMatchScore_UnrelatedCategory_DampensToThirtyPercent() {
        // "programming fundamentals" is NOT a substring of the skills list -> weight 0.3.
        // 100.0 * 0.3 = 30.0. Not 24.0 -- that value belongs to an 80.0 input.
        assertEquals(30.0,
                RecommendationEngine.calculateMatchScore(IRRELEVANT_CATEGORY, 100.0, FULL_STACK_SKILLS));
    }

    @Test
    @DisplayName("match score: null requiredSkills is treated as irrelevant, not as a crash")
    void calculateMatchScore_NullRequiredSkills_DampensToThirtyPercent() {
        assertEquals(30.0, RecommendationEngine.calculateMatchScore(RELEVANT_CATEGORY, 100.0, null));
    }

    @Test
    @DisplayName("match score: rounds to two decimals exactly as assessment-service does")
    void calculateMatchScore_RoundsToTwoDecimals() {
        // 46.67 * 0.3 = 14.001 -> 14.0
        assertEquals(14.0,
                RecommendationEngine.calculateMatchScore(IRRELEVANT_CATEGORY, 46.67, FULL_STACK_SKILLS));
        // full relevance leaves the input untouched
        assertEquals(46.67,
                RecommendationEngine.calculateMatchScore(RELEVANT_CATEGORY, 46.67, FULL_STACK_SKILLS));
    }

    @Test
    @DisplayName("match score: is case insensitive on both sides of the comparison")
    void calculateMatchScore_IsCaseInsensitive() {
        assertEquals(100.0,
                RecommendationEngine.calculateMatchScore("WEB DEVELOPMENT", 100.0, FULL_STACK_SKILLS));
    }

    @Test
    @DisplayName("reason text: 80 and above gets the strongest tier, naming career and category")
    void generateCareerReasonText_ScoreAboveEighty_ReturnsStrongestTier() {
        String text = RecommendationEngine.generateCareerReasonText(
                "Backend Developer", "System Design", 85.0);

        assertTrue(text.startsWith("Excellent match!"), text);
        assertTrue(text.contains("Backend Developer"), text);
        assertTrue(text.contains("System Design"), text);
        assertTrue(text.contains("85.0%"), text);
    }

    @Test
    @DisplayName("reason text: 60 to 80 gets the middle tier")
    void generateCareerReasonText_ScoreBetweenSixtyAndEighty_ReturnsMiddleTier() {
        String text = RecommendationEngine.generateCareerReasonText(
                "Backend Developer", "System Design", 66.67);

        assertTrue(text.startsWith("Good match."), text);
        assertTrue(text.contains("66.7%"), text);
    }

    @Test
    @DisplayName("reason text: 40 to 60 gets the moderate tier")
    void generateCareerReasonText_ScoreBetweenFortyAndSixty_ReturnsModerateTier() {
        String text = RecommendationEngine.generateCareerReasonText(
                "Data Scientist", "Database & SQL", 46.67);

        assertTrue(text.startsWith("Moderate match."), text);
        assertTrue(text.contains("46.7%"), text);
    }

    @Test
    @DisplayName("reason text: below 40 gets the exploratory tier and quotes no percentage")
    void generateCareerReasonText_ScoreBelowForty_ReturnsWeakestTier() {
        String text = RecommendationEngine.generateCareerReasonText(
                "Mobile Developer", "Programming Fundamentals", 30.0);

        assertTrue(text.startsWith("Exploratory match."), text);
        assertTrue(text.contains("Mobile Developer"), text);
        assertTrue(text.contains("Programming Fundamentals"), text);
        // This tier deliberately withholds the number -- a low score is not worth restating.
        assertFalse(text.contains("%"), text);
    }

    @Test
    @DisplayName("strength text: 70 and above reads as strong, below reads as foundational")
    void generateStrengthText_HighScore_NamesTheCategory() {
        String strong = RecommendationEngine.generateStrengthText("Database & SQL", 75.0);
        assertTrue(strong.startsWith("Strong understanding of"), strong);
        assertTrue(strong.contains("Database & SQL"), strong);
        assertTrue(strong.contains("75.0%"), strong);

        String foundational = RecommendationEngine.generateStrengthText("Database & SQL", 40.0);
        assertTrue(foundational.startsWith("Foundational knowledge of"), foundational);
    }

    @Test
    @DisplayName("improvement text: each of the three bands names the category")
    void generateImprovementText_LowScore_NamesTheCategory() {
        String low = RecommendationEngine.generateImprovementText("Database & SQL", 30.0);
        assertTrue(low.contains("need significant strengthening"), low);
        assertTrue(low.contains("Database & SQL"), low);

        assertTrue(RecommendationEngine.generateImprovementText("Database & SQL", 60.0)
                .contains("Intermediate"));
        assertTrue(RecommendationEngine.generateImprovementText("Database & SQL", 80.0)
                .contains("Advanced"));
    }

    @Test
    @DisplayName("actionable advice: every band names the top career and gives a next step")
    void generateActionableAdvice_ReturnsAdviceForTopCareer() {
        String high = RecommendationEngine.generateActionableAdvice("Backend Developer", 75.0);
        assertTrue(high.startsWith("You are well-positioned for"), high);
        assertTrue(high.contains("Backend Developer"), high);

        String mid = RecommendationEngine.generateActionableAdvice("Backend Developer", 55.0);
        assertTrue(mid.startsWith("To strengthen your path to"), mid);

        // The band today's real 30.0 scores land in.
        String low = RecommendationEngine.generateActionableAdvice("Backend Developer", 30.0);
        assertTrue(low.startsWith("Focus on strengthening your fundamentals"), low);
        assertTrue(low.contains("Backend Developer"), low);
    }
}

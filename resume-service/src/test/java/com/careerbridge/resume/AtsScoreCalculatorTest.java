package com.careerbridge.resume;

import com.careerbridge.resume.dto.SkillDto;
import com.careerbridge.resume.dto.StudentProfileDto;
import com.careerbridge.resume.service.AtsScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Mockito -- the calculator is a pure function over a DTO.
 *
 * Keyword-list sizes these tests depend on, from AtsScoreCalculator:
 *   Data Scientist  = 10 (Python, Machine Learning, SQL, TensorFlow, Pandas, NumPy,
 *                         Scikit-learn, Jupyter, Data Analysis, Statistics)
 *   Backend Dev     = 13
 * Editing either list changes the expected numbers below, which is deliberate: the arithmetic is
 * the contract, so a silent list edit should fail loudly here rather than quietly move every
 * student's score.
 */
class AtsScoreCalculatorTest {

    private final AtsScoreCalculator calculator = new AtsScoreCalculator();

    private static StudentProfileDto profileWithSkills(String... skillNames) {
        return StudentProfileDto.builder()
                .userId(1L)
                .skills(Arrays.stream(skillNames)
                        .map(name -> SkillDto.builder().skillName(name).build())
                        .toList())
                .build();
    }

    @Test
    @DisplayName("calculate: every keyword of one career present scores exactly 100.00")
    void calculate_ExactMatch_Returns100() {
        Double score = calculator.calculate(profileWithSkills(
                "Python", "Machine Learning", "SQL", "TensorFlow", "Pandas",
                "NumPy", "Scikit-learn", "Jupyter", "Data Analysis", "Statistics"));

        assertEquals(100.0, score);
    }

    @Test
    @DisplayName("calculate: half of one career's keywords scores exactly 50.00")
    void calculate_PartialMatch_ReturnsExactRatio() {
        // These five appear only in the Data Scientist list, so no other career competes.
        Double score = calculator.calculate(profileWithSkills(
                "Python", "Machine Learning", "TensorFlow", "Pandas", "NumPy"));

        assertEquals(50.0, score);
    }

    /**
     * The bug this pins, and the reason the implementation scores every career and takes the
     * highest PERCENTAGE rather than picking the career with the most raw matches first:
     *
     *   Data Scientist    5 matched / 10 keywords = 50.00%
     *   Backend Developer 6 matched / 13 keywords = 46.15%
     *
     * Backend wins on raw count (6 > 5) but loses on ratio. A count-first implementation returns
     * 46.15 here; the correct answer is 50.00. Skills are chosen so no keyword overlaps between the
     * two lists -- "SQL" is deliberately absent, since it belongs to both and would hand Backend a
     * seventh match.
     */
    @Test
    @DisplayName("calculate: the best RATIO wins, not the highest raw match count")
    void calculate_PrefersBestRatioOverHighestCount() {
        Double score = calculator.calculate(profileWithSkills(
                // 5 of Data Scientist's 10
                "Python", "Machine Learning", "TensorFlow", "Pandas", "NumPy",
                // 6 of Backend Developer's 13
                "Java", "Spring Boot", "Spring", "REST API", "PostgreSQL", "MySQL"));

        assertEquals(50.0, score, "expected Data Scientist's 5/10, not Backend's 6/13");
    }

    @Test
    @DisplayName("calculate: matching is case-insensitive in both directions")
    void calculate_CaseInsensitive() {
        Double lower = calculator.calculate(profileWithSkills(
                "python", "machine learning", "tensorflow", "pandas", "numpy"));
        Double upper = calculator.calculate(profileWithSkills(
                "PYTHON", "MACHINE LEARNING", "TENSORFLOW", "PANDAS", "NUMPY"));

        assertEquals(50.0, lower);
        assertEquals(50.0, upper);
    }

    @Test
    @DisplayName("calculate: an empty skills list scores 0.00 rather than throwing")
    void calculate_NoSkills_ReturnsZero() {
        assertEquals(0.0, calculator.calculate(
                StudentProfileDto.builder().userId(1L).skills(Collections.emptyList()).build()));
    }

    @Test
    @DisplayName("calculate: a null skills list scores 0.00")
    void calculate_NullSkillsList_ReturnsZero() {
        assertEquals(0.0, calculator.calculate(StudentProfileDto.builder().userId(1L).build()));
    }

    @Test
    @DisplayName("calculate: a null profile scores 0.00")
    void calculate_NullProfile_ReturnsZero() {
        assertEquals(0.0, calculator.calculate(null));
    }

    /** A payload can carry a null entry or a blank name; neither may reach a NullPointerException. */
    @Test
    @DisplayName("calculate: null and blank skill names are skipped, not dereferenced")
    void calculate_NullAndBlankSkillNames_AreSkipped() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .userId(1L)
                .skills(Arrays.asList(
                        SkillDto.builder().skillName("Python").build(),
                        SkillDto.builder().skillName(null).build(),
                        SkillDto.builder().skillName("   ").build(),
                        null))
                .build();

        // 1 of Data Scientist's 10.
        assertEquals(10.0, calculator.calculate(profile));
    }

    @Test
    @DisplayName("calculate: skills matching no career at all score 0.00")
    void calculate_NoKeywordOverlap_ReturnsZero() {
        assertEquals(0.0, calculator.calculate(profileWithSkills("Underwater Basket Weaving", "Latin")));
    }

    /**
     * The score is a percentage of one career's expected stack, so it is bounded. A student cannot
     * exceed 100 by listing every keyword of every career.
     */
    @Test
    @DisplayName("calculate: the score never exceeds 100.00 even with skills spanning every career")
    void calculate_AllCareersMatched_StillCappedAt100() {
        Double score = calculator.calculate(profileWithSkills(
                "Java", "Spring Boot", "Spring", "REST API", "SQL", "PostgreSQL", "MySQL",
                "Maven", "Git", "Hibernate", "JPA", "Microservices", "Docker",
                "React", "JavaScript", "TypeScript", "HTML", "CSS", "Tailwind",
                "Python", "Machine Learning", "TensorFlow", "Pandas", "NumPy"));

        assertTrue(score <= 100.0, "score exceeded 100: " + score);
        // Backend Developer is fully covered by the list above: 13/13.
        assertEquals(100.0, score);
    }

    @Test
    @DisplayName("calculate: the result is rounded to two decimals, matching prs-service's round2")
    void calculate_RoundsToTwoDecimals() {
        // 1 of DevOps Engineer's 11 = 9.0909...%, which must surface as 9.09 not 9.090909090909092.
        Double score = calculator.calculate(profileWithSkills("Kubernetes"));

        assertEquals(9.09, score);
    }

    /** The score feeds prs-service's resumeScore, which is weighted as a 0-100 input. */
    @Test
    @DisplayName("calculate: the returned value is always within the legal 0-100 range")
    void calculate_AlwaysWithinLegalRange() {
        List<StudentProfileDto> cases = List.of(
                profileWithSkills("Java"),
                profileWithSkills("Python", "SQL"),
                profileWithSkills("Nothing", "Relevant"));

        for (StudentProfileDto profile : cases) {
            Double score = calculator.calculate(profile);
            assertTrue(score >= 0.0 && score <= 100.0, "out of range: " + score);
        }
    }
}

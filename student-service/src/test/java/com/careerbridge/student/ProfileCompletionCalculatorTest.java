package com.careerbridge.student;

import com.careerbridge.student.model.Education;
import com.careerbridge.student.model.Project;
import com.careerbridge.student.model.Skill;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.util.ProfileCompletionCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The one piece of real arithmetic in this service, so it is asserted directly. */
class ProfileCompletionCalculatorTest {

    private StudentProfile fullProfile() {
        return StudentProfile.builder()
                .firstName("Ada").lastName("Lovelace").phone("+91-9000000000")
                .bio("Final year CS student").city("Pune")
                .resumeUrl("https://cdn/resume.pdf")
                .linkedinUrl("https://linkedin.com/in/ada")
                .portfolioUrl("https://ada.dev")
                .build();
    }

    @Test
    @DisplayName("every criterion met scores exactly 100")
    void fullProfile_Returns100() {
        int score = ProfileCompletionCalculator.calculateCompletion(
                fullProfile(),
                List.of(Skill.builder().build(), Skill.builder().build()),
                List.of(Education.builder().build()),
                List.of(Project.builder().build()));

        assertEquals(100, score);
    }

    @Test
    @DisplayName("an empty profile with no children scores 0")
    void emptyProfile_Returns0() {
        int score = ProfileCompletionCalculator.calculateCompletion(
                StudentProfile.builder().build(), List.of(), List.of(), List.of());

        assertEquals(0, score);
    }

    @Test
    @DisplayName("one skill is not enough: the skills criterion needs two")
    void oneSkill_DoesNotEarnSkillWeight() {
        int score = ProfileCompletionCalculator.calculateCompletion(
                StudentProfile.builder().build(),
                List.of(Skill.builder().build()),
                List.of(), List.of());

        assertEquals(0, score);

        int withTwo = ProfileCompletionCalculator.calculateCompletion(
                StudentProfile.builder().build(),
                List.of(Skill.builder().build(), Skill.builder().build()),
                List.of(), List.of());

        assertEquals(15, withTwo);
    }

    @Test
    @DisplayName("basic info is all-or-nothing: four of five fields earns nothing")
    void partialBasicInfo_EarnsNothing() {
        StudentProfile fourOfFive = StudentProfile.builder()
                .firstName("Ada").lastName("Lovelace").phone("+91-9000000000").bio("Bio")
                .build();

        assertEquals(0, ProfileCompletionCalculator.calculateCompletion(
                fourOfFive, List.of(), List.of(), List.of()));
    }

    /**
     * A full-replace PUT leaves "" where the user cleared a field. Blank must not count as filled,
     * or a cleared profile would keep scoring as complete.
     */
    @Test
    @DisplayName("blank strings count as absent, not filled")
    void blankFields_CountAsAbsent() {
        StudentProfile blanked = StudentProfile.builder()
                .firstName("Ada").lastName("Lovelace").phone("+91-9000000000").bio("Bio").city("   ")
                .githubUrl("")
                .build();

        assertEquals(0, ProfileCompletionCalculator.calculateCompletion(
                blanked, List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("either linkedin or github earns the social weight; nulls are safe")
    void githubOnly_EarnsSocialWeight() {
        StudentProfile githubOnly = StudentProfile.builder().githubUrl("https://github.com/ada").build();

        assertEquals(10, ProfileCompletionCalculator.calculateCompletion(
                githubOnly, null, null, null));
    }
}

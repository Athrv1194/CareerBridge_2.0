package com.careerbridge.student.util;

import com.careerbridge.student.model.Education;
import com.careerbridge.student.model.Project;
import com.careerbridge.student.model.Skill;
import com.careerbridge.student.model.StudentProfile;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Scores how complete a student profile is, 0-100.
 *
 * Certificates and social-link rows are deliberately not inputs -- they carry no weight in the
 * breakdown below, which is why StudentServiceImpl.addCertificate skips the recalculation.
 */
public class ProfileCompletionCalculator {

    // Weights, summing to exactly 100.
    private static final int BASIC_INFO = 20;
    private static final int EDUCATION = 15;
    private static final int SKILLS = 15;
    private static final int PROJECTS = 20;
    private static final int RESUME = 15;
    private static final int SOCIAL = 10;
    private static final int PORTFOLIO = 5;

    private static final int MIN_SKILLS = 2;

    public static Integer calculateCompletion(StudentProfile profile,
                                              List<Skill> skills,
                                              List<Education> educations,
                                              List<Project> projects) {
        if (profile == null) {
            return 0;
        }

        int score = 0;

        // All five basic fields, not partial credit: the other six criteria are booleans too.
        if (filled(profile.getFirstName())
                && filled(profile.getLastName())
                && filled(profile.getPhone())
                && filled(profile.getBio())
                && filled(profile.getCity())) {
            score += BASIC_INFO;
        }

        if (size(educations) >= 1) {
            score += EDUCATION;
        }
        if (size(skills) >= MIN_SKILLS) {
            score += SKILLS;
        }
        if (size(projects) >= 1) {
            score += PROJECTS;
        }
        if (filled(profile.getResumeUrl())) {
            score += RESUME;
        }
        if (filled(profile.getLinkedinUrl()) || filled(profile.getGithubUrl())) {
            score += SOCIAL;
        }
        if (filled(profile.getPortfolioUrl())) {
            score += PORTFOLIO;
        }

        return score;
    }

    /**
     * Blank counts as absent. A full-replace PUT can leave "" behind where the user cleared a
     * field, and an empty string is not a filled-in profile field.
     */
    private static boolean filled(String value) {
        return StringUtils.hasText(value);
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private ProfileCompletionCalculator() {
    }
}

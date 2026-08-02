package com.careerbridge.resume.service;

import com.careerbridge.resume.dto.SkillDto;
import com.careerbridge.resume.dto.StudentProfileDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based ATS score: what fraction of a career's expected keyword set the student's declared
 * skills cover, 0.00-100.00.
 *
 * Keyed on the seven careers CareerCatalog.java (recommendation-service) and
 * RoadmapDataSeeder.java (roadmap-service) both use, verified identical between those two files.
 * This is a THIRD copy of that list -- a fourth counting assessment-service's data.sql -- and it
 * must be kept in the same set of names. A rename on any of those three sides is silent here: the
 * keyed lookup below simply never matches and every student falls through to best-match.
 *
 * There is no StudentProfileDto.careerPath -- no such field exists on StudentProfile. The keyed
 * lookup is kept so wiring a real career input is a one-line change the day recommendation-service
 * exposes a batch endpoint, but it is currently unreachable; only the best-match branch runs.
 */
@Component
public class AtsScoreCalculator {

    private static final Map<String, List<String>> CAREER_KEYWORDS = Map.of(
            "Full Stack Developer", List.of(
                    "Java", "Spring Boot", "React", "JavaScript", "SQL", "REST API",
                    "Git", "Docker", "HTML", "CSS", "Maven", "Hibernate"),
            "Backend Developer", List.of(
                    "Java", "Spring Boot", "Spring", "REST API", "SQL", "PostgreSQL",
                    "MySQL", "Maven", "Git", "Hibernate", "JPA", "Microservices", "Docker"),
            "Frontend Developer", List.of(
                    "React", "JavaScript", "TypeScript", "HTML", "CSS", "Tailwind",
                    "Node.js", "Redux", "Webpack", "Vite", "REST API", "Git"),
            "Data Scientist", List.of(
                    "Python", "Machine Learning", "SQL", "TensorFlow", "Pandas",
                    "NumPy", "Scikit-learn", "Jupyter", "Data Analysis", "Statistics"),
            "DevOps Engineer", List.of(
                    "Docker", "Kubernetes", "CI/CD", "Linux", "AWS", "Git", "Jenkins",
                    "Ansible", "Terraform", "Bash", "Monitoring"),
            "Mobile Developer", List.of(
                    "Kotlin", "Java", "Swift", "Android SDK", "iOS", "Flutter",
                    "React Native", "REST API", "Firebase", "Git", "SQLite", "MVVM"),
            "System Design Engineer", List.of(
                    "System Design", "Scalability", "Microservices", "Distributed Systems",
                    "Load Balancing", "Caching", "Message Queues", "Docker", "Kubernetes",
                    "Database Design", "API Design"));

    /**
     * Never throws. A null profile, null skills, or empty skills list all score 0.0 -- there is
     * nothing to match against.
     */
    public Double calculate(StudentProfileDto profile) {
        if (profile == null || profile.getSkills() == null || profile.getSkills().isEmpty()) {
            return 0.0;
        }

        // The null-element filter comes first: a null entry inside the list is a different failure
        // from a null list, and getSkillName on one is an NPE. Same guard as ResumePdfBuilder's
        // skills section, for the same reason -- a partially-populated child row from
        // student-service can carry one.
        Set<String> studentSkillsLower = profile.getSkills().stream()
                .filter(Objects::nonNull)
                .map(SkillDto::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (studentSkillsLower.isEmpty()) {
            return 0.0;
        }

        // Best match by RATIO (matched / list size), never raw match count -- a 5/10 career (50%)
        // must outrank a 6/13 career (46%) even though 6 > 5. Scoring every career and comparing
        // the resulting percentage, rather than comparing counts first, is what keeps this correct.
        return CAREER_KEYWORDS.values().stream()
                .map(keywords -> scoreAgainst(keywords, studentSkillsLower))
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }

    private double scoreAgainst(List<String> keywords, Set<String> studentSkillsLower) {
        if (keywords.isEmpty()) {
            return 0.0;
        }

        long matched = keywords.stream()
                .filter(kw -> studentSkillsLower.contains(kw.toLowerCase(Locale.ROOT)))
                .count();

        return round2(((double) matched / keywords.size()) * 100.0);
    }

    /** Matches prs-service's own round2 convention: 2 decimal places, half-up. */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

package com.careerbridge.resume.service;

import com.careerbridge.resume.dto.AtsResult;
import com.careerbridge.resume.dto.SkillDto;
import com.careerbridge.resume.dto.StudentProfileDto;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
 *
 * The closest-matching career and its matched/missing keywords used to be computed here and
 * immediately discarded -- calculate() returned only the bare score. calculateDetailed() and
 * calculateTailored() below are what the Resume page's ATS breakdown card and "tailor mode" are
 * wired to; calculate() is kept only as a thin wrapper for anything that just wants the number.
 */
@Component
public class AtsScoreCalculator {

    private static final Map<String, List<String>> CAREER_KEYWORDS = new LinkedHashMap<>();

    static {
        CAREER_KEYWORDS.put("Full Stack Developer", List.of(
                "Java", "Spring Boot", "React", "JavaScript", "SQL", "REST API",
                "Git", "Docker", "HTML", "CSS", "Maven", "Hibernate"));
        CAREER_KEYWORDS.put("Backend Developer", List.of(
                "Java", "Spring Boot", "Spring", "REST API", "SQL", "PostgreSQL",
                "MySQL", "Maven", "Git", "Hibernate", "JPA", "Microservices", "Docker"));
        CAREER_KEYWORDS.put("Frontend Developer", List.of(
                "React", "JavaScript", "TypeScript", "HTML", "CSS", "Tailwind",
                "Node.js", "Redux", "Webpack", "Vite", "REST API", "Git"));
        CAREER_KEYWORDS.put("Data Scientist", List.of(
                "Python", "Machine Learning", "SQL", "TensorFlow", "Pandas",
                "NumPy", "Scikit-learn", "Jupyter", "Data Analysis", "Statistics"));
        CAREER_KEYWORDS.put("DevOps Engineer", List.of(
                "Docker", "Kubernetes", "CI/CD", "Linux", "AWS", "Git", "Jenkins",
                "Ansible", "Terraform", "Bash", "Monitoring"));
        CAREER_KEYWORDS.put("Mobile Developer", List.of(
                "Kotlin", "Java", "Swift", "Android SDK", "iOS", "Flutter",
                "React Native", "REST API", "Firebase", "Git", "SQLite", "MVVM"));
        CAREER_KEYWORDS.put("System Design Engineer", List.of(
                "System Design", "Scalability", "Microservices", "Distributed Systems",
                "Load Balancing", "Caching", "Message Queues", "Docker", "Kubernetes",
                "Database Design", "API Design"));
    }

    /** Every keyword across all seven careers, deduped case-insensitively, canonical casing kept. */
    private static final List<String> ALL_KEYWORDS;

    static {
        Set<String> seenLower = new LinkedHashSet<>();
        List<String> all = new ArrayList<>();
        for (List<String> keywords : CAREER_KEYWORDS.values()) {
            for (String kw : keywords) {
                if (seenLower.add(kw.toLowerCase(Locale.ROOT))) {
                    all.add(kw);
                }
            }
        }
        ALL_KEYWORDS = List.copyOf(all);
    }

    /** Never throws. A null profile, null skills, or empty skills list all score 0.0. */
    public Double calculate(StudentProfileDto profile) {
        return calculateDetailed(profile).getScore();
    }

    /**
     * Best-match against the seven fixed careers, with the full breakdown -- which career matched
     * best, and exactly which of its keywords the student does and does not have.
     */
    public AtsResult calculateDetailed(StudentProfileDto profile) {
        Set<String> studentSkillsLower = skillsLower(profile);

        if (studentSkillsLower.isEmpty()) {
            return AtsResult.builder().score(0.0).closestCareerName(null)
                    .matchedKeywords(List.of()).missingKeywords(List.of()).totalKeywords(0).build();
        }

        String bestCareer = null;
        double bestScore = -1;
        for (Map.Entry<String, List<String>> entry : CAREER_KEYWORDS.entrySet()) {
            double score = scoreAgainst(entry.getValue(), studentSkillsLower);
            if (score > bestScore) {
                bestScore = score;
                bestCareer = entry.getKey();
            }
        }

        List<String> keywords = CAREER_KEYWORDS.get(bestCareer);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String kw : keywords) {
            (studentSkillsLower.contains(kw.toLowerCase(Locale.ROOT)) ? matched : missing).add(kw);
        }

        return AtsResult.builder()
                .score(round2(bestScore))
                .closestCareerName(bestCareer)
                .matchedKeywords(matched)
                .missingKeywords(missing)
                .totalKeywords(keywords.size())
                .build();
    }

    /**
     * "Tailor mode": required keywords are extracted from the pasted job description text instead
     * of coming from one of the seven fixed career lists. Falls back to calculateDetailed when the
     * text contains none of the ~70 keywords this service recognizes -- an empty required set would
     * otherwise divide by zero, and silently scoring 0% for an unrecognized job description reads
     * as "you have no matching skills" when the truth is "this text named nothing we know".
     */
    public AtsResult calculateTailored(StudentProfileDto profile, String jobDescription) {
        if (!StringUtils.hasText(jobDescription)) {
            return calculateDetailed(profile);
        }

        List<String> required = extractKeywords(jobDescription);
        if (required.isEmpty()) {
            return calculateDetailed(profile);
        }

        Set<String> studentSkillsLower = skillsLower(profile);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String kw : required) {
            (studentSkillsLower.contains(kw.toLowerCase(Locale.ROOT)) ? matched : missing).add(kw);
        }

        double score = required.isEmpty() ? 0.0 : round2(((double) matched.size() / required.size()) * 100.0);

        return AtsResult.builder()
                .score(score)
                .closestCareerName(null)
                .matchedKeywords(matched)
                .missingKeywords(missing)
                .totalKeywords(required.size())
                .build();
    }

    /**
     * Whole-word (well, whole-token) match, not a bare substring: "Git" as a plain substring would
     * false-positive inside "GitHub", and \b sits at the word-character boundary so "Git" only
     * matches when it is not immediately followed by another word character. Case-insensitive.
     */
    private List<String> extractKeywords(String jobDescription) {
        List<String> found = new ArrayList<>();
        for (String keyword : ALL_KEYWORDS) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(jobDescription).find()) {
                found.add(keyword);
            }
        }
        return found;
    }

    private Set<String> skillsLower(StudentProfileDto profile) {
        if (profile == null || profile.getSkills() == null || profile.getSkills().isEmpty()) {
            return Set.of();
        }
        // Null-element filter first: a null entry inside the list is a different failure from a
        // null list, and getSkillName on one is an NPE. Same guard as ResumePdfBuilder's skills
        // section, for the same reason -- a partially-populated child row from student-service can
        // carry one.
        return profile.getSkills().stream()
                .filter(Objects::nonNull)
                .map(SkillDto::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
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

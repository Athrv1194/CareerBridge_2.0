package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.CandidateResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.PublicStudentProfileDto;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.util.SkillsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Candidate search is exactly two outbound HTTP calls, regardless of how many students exist:
 *
 *   1. student-service GET /api/student/profiles/public  -> identity + skills for every public profile
 *   2. prs-service     GET /api/prs/leaderboard          -> studentId -> totalScore for every student
 *
 * Then all filtering happens in memory. The alternative shape -- fetch a roster, then one profile
 * call per candidate -- is N+1 synchronous HTTP calls on a request thread at a 3s timeout each,
 * which is a hang waiting to happen on a real student body. Neither upstream endpoint takes filter
 * parameters, so pushing the predicates down is not currently possible; if the student body grows
 * past what one response can carry, the fix is a filtered/paged endpoint on student-service, not a
 * per-candidate loop here.
 *
 * Both clients are fail-soft. A student-service outage yields an empty result (there are no
 * candidates to show); a prs-service outage yields candidates with SCORE_UNAVAILABLE rather than
 * no candidates at all.
 */
@Service
public class CandidateSearchServiceImpl implements CandidateSearchService {

    private static final Logger log = LoggerFactory.getLogger(CandidateSearchServiceImpl.class);

    private static final Set<String> SEARCH_ROLES =
            Set.of(RecruiterRoles.RECRUITER, RecruiterRoles.PLACEMENT_OFFICER, RecruiterRoles.SUPER_ADMIN);

    /**
     * No PRS row for this student, or prs-service was unreachable. Deliberately outside the legal
     * 0-100 range so it cannot be mistaken for a real score, matching prs-service's own
     * StudentServiceClient.PROFILE_FETCH_FAILED convention.
     */
    private static final double SCORE_UNAVAILABLE = -1.0;

    private static final String PROFILE_URL_PREFIX = "/api/student/profile/";

    private final StudentServiceClient studentServiceClient;
    private final PrsServiceClient prsServiceClient;

    public CandidateSearchServiceImpl(StudentServiceClient studentServiceClient,
                                      PrsServiceClient prsServiceClient) {
        this.studentServiceClient = studentServiceClient;
        this.prsServiceClient = prsServiceClient;
    }

    @Override
    public List<CandidateResponse> searchCandidates(String callerRole, String skills,
                                                    Double minScore, Double maxScore) {
        if (!SEARCH_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only RECRUITER, PLACEMENT_OFFICER or SUPER_ADMIN may search candidates",
                    HttpStatus.FORBIDDEN);
        }

        List<PublicStudentProfileDto> profiles = studentServiceClient.fetchPublicProfiles(callerRole);
        if (profiles.isEmpty()) {
            log.warn("No public student profiles available for candidate search");
            return List.of();
        }

        Map<Long, Double> scoresByStudentId = prsServiceClient.fetchGlobalLeaderboard().stream()
                .filter(entry -> entry.getStudentId() != null && entry.getTotalScore() != null)
                .collect(Collectors.toMap(PrsLeaderboardEntryDto::getStudentId,
                        PrsLeaderboardEntryDto::getTotalScore,
                        // prs-service enforces one row per student, so a duplicate key here means
                        // something upstream changed; keep the first rather than throwing.
                        (first, second) -> first));

        Set<String> wantedSkills = SkillsParser.parse(skills).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return profiles.stream()
                .filter(profile -> profile.getStudentId() != null)
                .filter(profile -> matchesSkills(profile, wantedSkills))
                .map(profile -> toCandidate(profile,
                        scoresByStudentId.getOrDefault(profile.getStudentId(), SCORE_UNAVAILABLE)))
                .filter(candidate -> withinScoreRange(candidate.getPrsScore(), minScore, maxScore))
                // Highest score first; SCORE_UNAVAILABLE is -1.0 so it naturally sorts last.
                .sorted(Comparator.comparingDouble(CandidateResponse::getPrsScore).reversed())
                .toList();
    }

    private boolean matchesSkills(PublicStudentProfileDto profile, Set<String> wantedSkills) {
        if (wantedSkills.isEmpty()) {
            return true;
        }

        List<String> held = profile.getSkills();
        if (held == null || held.isEmpty()) {
            return false;
        }

        return held.stream()
                .filter(Objects::nonNull)
                .anyMatch(skill -> wantedSkills.contains(skill.toLowerCase(Locale.ROOT)));
    }

    /**
     * An unavailable score fails a minScore filter but passes a maxScore filter: "at least 60"
     * is a claim the data cannot support, while "at most 60" has not been contradicted. Silently
     * treating unknown as 0 would do both wrong.
     */
    private boolean withinScoreRange(Double score, Double minScore, Double maxScore) {
        boolean unavailable = score == SCORE_UNAVAILABLE;

        if (minScore != null && (unavailable || score < minScore)) {
            return false;
        }

        return maxScore == null || unavailable || score <= maxScore;
    }

    private CandidateResponse toCandidate(PublicStudentProfileDto profile, double prsScore) {
        return CandidateResponse.builder()
                .studentId(profile.getStudentId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .email(profile.getEmail())
                .skills(profile.getSkills() == null ? List.of() : profile.getSkills())
                .prsScore(prsScore)
                .profileCompletionPercentage(profile.getProfileCompletionPercentage())
                .profileUrl(PROFILE_URL_PREFIX + profile.getStudentId())
                .build();
    }
}

package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.PublicStudentProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fetches the public candidate pool from student-service, the source
 * CandidateSearchServiceImpl filters and PrsServiceClient's scores enrich.
 *
 * A concrete @Service with no interface, matching prs-service's own StudentServiceClient and
 * notification-service's EmailService: one implementation, and Mockito mocks classes fine.
 */
@Service
public class StudentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceClient.class);

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String PUBLIC_PROFILES_PATH = "/api/student/profiles/public";

    private final RestClient studentRestClient;

    public StudentServiceClient(@Qualifier("studentRestClient") RestClient studentRestClient) {
        this.studentRestClient = studentRestClient;
    }

    /**
     * Never throws. A student-service outage must not 500 a candidate search or org-scoped
     * application list -- an empty result is the correct fail-soft answer, same contract as
     * prs-service's fetchProfileScore.
     *
     * Forwards the caller's real role: student-service runs its own RBAC on this endpoint
     * (RECRUITER/PLACEMENT_OFFICER/ORG_ADMIN/SUPER_ADMIN only), and recruiter-service has
     * already checked the same role before reaching here, so this is not privilege elevation --
     * unlike PrsServiceClient.fetchLeaderboard, which is.
     */
    public List<PublicStudentProfileDto> fetchPublicProfiles(String callerRole) {
        try {
            List<PublicStudentProfileDto> profiles = studentRestClient.get()
                    .uri(PUBLIC_PROFILES_PATH)
                    .header(USER_ROLE_HEADER, callerRole)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PublicStudentProfileDto>>() {});

            return profiles == null ? List.of() : profiles;
        } catch (Exception ex) {
            log.warn("Failed to fetch public student profiles: {}", ex.getMessage());
            return List.of();
        }
    }
}

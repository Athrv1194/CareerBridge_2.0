package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.PublicStudentProfileDto;
import com.careerbridge.recruiter.dto.StudentDepartmentDto;
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
    private static final String STUDENT_DEPARTMENTS_PATH = "/api/student/profiles/departments";

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

    /**
     * studentId -> department for every student, backing the department breakdown on org-scoped
     * placement stats.
     *
     * A different endpoint from fetchPublicProfiles above, not a reuse of it: that one filters
     * isPublic=true, and a student who switched their profile to private would silently disappear
     * from their department's placement numbers. It also carries name and email, which a headcount
     * does not need.
     *
     * Never throws, same fail-soft contract as fetchPublicProfiles: a student-service outage costs
     * the breakdown, not the whole stats response.
     */
    public List<StudentDepartmentDto> fetchStudentDepartments(String callerRole) {
        try {
            List<StudentDepartmentDto> departments = studentRestClient.get()
                    .uri(STUDENT_DEPARTMENTS_PATH)
                    .header(USER_ROLE_HEADER, callerRole)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<StudentDepartmentDto>>() {});

            return departments == null ? List.of() : departments;
        } catch (Exception ex) {
            log.warn("Failed to fetch student departments: {}", ex.getMessage());
            return List.of();
        }
    }
}

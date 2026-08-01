package com.careerbridge.prs.service;

import com.careerbridge.prs.dto.StudentProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches profileCompletionPercentage from student-service, the 20% input to the Placement
 * Readiness Score.
 *
 * A concrete @Service with no interface, matching notification-service's EmailService: one
 * implementation, and Mockito mocks classes fine.
 *
 * Calls student-service directly on the compose network, NOT through api-gateway. The gateway
 * exists to validate JWTs from outside the system; this call originates inside it and carries no
 * token to validate. student-service trusts X-User-Id blindly, which is exactly why its port is
 * never published to the host.
 */
@Service
public class StudentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceClient.class);

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String PROFILE_PATH = "/api/student/profile";

    /**
     * Returned when the profile score could not be determined, for ANY reason: student-service
     * down, timed out, 404 for a student with no profile row yet, malformed body, or a null field.
     *
     * The contract is "keep the last known profileScore, do not overwrite" -- callers must compare
     * against this before assigning. Writing it into PlacementReadinessScore.profileScore would
     * turn a transient outage into a permanently wrong score, and -1.0 is chosen precisely because
     * it is outside the legal 0-100 range, so it fails a range check loudly if it ever leaks.
     */
    public static final double PROFILE_FETCH_FAILED = -1.0;

    private final RestClient studentRestClient;

    public StudentServiceClient(RestClient studentRestClient) {
        this.studentRestClient = studentRestClient;
    }

    /**
     * Never throws. A student-service outage must not stop a PRS update: the assessment and roadmap
     * inputs are already known, and recomputing the total from two fresh inputs plus one stale one
     * is strictly better than dropping the event.
     *
     * The broad catch is deliberate. RestClient.retrieve() throws on any 4xx/5xx, the request
     * factory throws on connect and read timeouts, and Jackson throws on a body it cannot bind --
     * all of them mean the same thing here, and none of them should reach the listener.
     */
    public double fetchProfileScore(Long studentId) {
        if (studentId == null) {
            log.warn("Cannot fetch profile score for a null studentId");
            return PROFILE_FETCH_FAILED;
        }

        try {
            StudentProfileResponse profile = studentRestClient.get()
                    .uri(PROFILE_PATH)
                    .header(USER_ID_HEADER, studentId.toString())
                    .retrieve()
                    .body(StudentProfileResponse.class);

            if (profile == null || profile.getProfileCompletionPercentage() == null) {
                // Reachable two ways: an empty body, or a rename of profileCompletionPercentage in
                // student-service. Jackson 3 ignores unknown properties, so a rename binds to null
                // here rather than throwing -- this guard is the only thing that catches it.
                log.warn("student-service returned no profileCompletionPercentage for studentId={}", studentId);
                return PROFILE_FETCH_FAILED;
            }

            return profile.getProfileCompletionPercentage().doubleValue();
        } catch (Exception ex) {
            log.warn("Failed to fetch profile score for studentId={}: {}. Keeping last known value.",
                    studentId, ex.getMessage());
            return PROFILE_FETCH_FAILED;
        }
    }
}

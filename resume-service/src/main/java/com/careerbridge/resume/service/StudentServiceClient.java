package com.careerbridge.resume.service;

import com.careerbridge.resume.dto.StudentProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches the student's own full profile -- the source for both PDF rendering and ATS scoring.
 *
 * A concrete @Service with no interface, matching prs-service's own StudentServiceClient and
 * notification-service's EmailService: one implementation, and Mockito mocks classes fine.
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

    private final RestClient studentRestClient;

    public StudentServiceClient(RestClient studentRestClient) {
        this.studentRestClient = studentRestClient;
    }

    /**
     * Never throws. Returns null on ANY failure -- student-service down, timed out, or a student
     * with no profile row yet -- and the caller (ResumeServiceImpl) turns that into a 503, not a
     * 500: the failure is a downstream dependency being unavailable, not this service's own fault.
     *
     * Forwards X-User-Id as the studentId itself, impersonating the caller on the compose network.
     * Same pattern as prs-service's StudentServiceClient: student-service has no Spring Security
     * and trusts this header blindly, which is exactly why port 8082 is never published to the
     * host.
     */
    public StudentProfileDto fetchMyProfile(Long studentId) {
        if (studentId == null) {
            log.warn("Cannot fetch a profile for a null studentId");
            return null;
        }

        try {
            return studentRestClient.get()
                    .uri(PROFILE_PATH)
                    .header(USER_ID_HEADER, studentId.toString())
                    .retrieve()
                    .body(StudentProfileDto.class);
        } catch (Exception ex) {
            log.warn("Failed to fetch student profile for studentId={}: {}", studentId, ex.getMessage());
            return null;
        }
    }
}

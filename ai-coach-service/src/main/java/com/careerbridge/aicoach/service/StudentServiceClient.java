package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.client.StudentProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Calls student-service directly on the compose network, never through api-gateway. Forwards
 * X-User-Id as the studentId itself, same convention as resume-service's own StudentServiceClient.
 *
 * Never throws. Returns null on ANY failure -- the coaching prompt is built with whatever context
 * is available; a downstream outage should degrade the prompt, not fail the chat.
 */
@Service
public class StudentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceClient.class);

    private static final String PROFILE_PATH = "/api/student/profile";

    private final RestClient studentRestClient;

    public StudentServiceClient(@Qualifier("studentRestClient") RestClient studentRestClient) {
        this.studentRestClient = studentRestClient;
    }

    public StudentProfileDto fetchMyProfile(Long studentId) {
        if (studentId == null) {
            log.warn("Cannot fetch a profile for a null studentId");
            return null;
        }

        try {
            return studentRestClient.get()
                    .uri(PROFILE_PATH)
                    .header(AiCoachConstants.USER_ID_HEADER, studentId.toString())
                    .retrieve()
                    .body(StudentProfileDto.class);
        } catch (Exception ex) {
            log.warn("Failed to fetch student profile for studentId={}: {}", studentId, ex.getMessage());
            return null;
        }
    }
}

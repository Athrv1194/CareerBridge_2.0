package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.client.PrsResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * GET /api/prs/my is self-scoped on X-User-Id -- no role elevation needed, unlike
 * recruiter-service's PrsServiceClient which must elevate to read the leaderboard endpoint.
 *
 * Never throws. Returns null on ANY failure -- same fail-soft contract as StudentServiceClient.
 */
@Service
public class PrsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PrsServiceClient.class);

    private static final String MY_PRS_PATH = "/api/prs/my";

    private final RestClient prsRestClient;

    public PrsServiceClient(@Qualifier("prsRestClient") RestClient prsRestClient) {
        this.prsRestClient = prsRestClient;
    }

    public PrsResponseDto fetchMyPrs(Long studentId) {
        if (studentId == null) {
            log.warn("Cannot fetch PRS for a null studentId");
            return null;
        }

        try {
            return prsRestClient.get()
                    .uri(MY_PRS_PATH)
                    .header(AiCoachConstants.USER_ID_HEADER, studentId.toString())
                    .retrieve()
                    .body(PrsResponseDto.class);
        } catch (Exception ex) {
            log.warn("Failed to fetch PRS for studentId={}: {}", studentId, ex.getMessage());
            return null;
        }
    }
}

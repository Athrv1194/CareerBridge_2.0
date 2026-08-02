package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.client.RoadmapResponseDto;
import com.careerbridge.aicoach.dto.client.RoadmapTemplateResponseDto;
import com.careerbridge.aicoach.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * The one three-state client in this service (R1 in the plan). GET /api/roadmap/my returns 404
 * for a student with no roadmap yet -- that is a real, expected state, not a fault, and must not
 * look the same as roadmap-service being unreachable. Catch order is deliberate:
 * HttpClientErrorException.NotFound is caught FIRST and returns null (-> caller returns []);
 * everything else throws 503. Never use .onStatus(4xx, noop) here -- Jackson 3 has
 * FAIL_ON_UNKNOWN_PROPERTIES off, so a 404 error body would bind cleanly into an all-null
 * RoadmapResponseDto and an outage would be indistinguishable from "no roadmap".
 */
@Service
public class RoadmapServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RoadmapServiceClient.class);

    private static final String MY_ROADMAP_PATH = "/api/roadmap/my";
    private static final String TEMPLATES_PATH = "/api/roadmap/templates";

    private final RestClient roadmapRestClient;

    public RoadmapServiceClient(@Qualifier("roadmapRestClient") RestClient roadmapRestClient) {
        this.roadmapRestClient = roadmapRestClient;
    }

    /**
     * Returns null when the student genuinely has no roadmap (404). Throws CustomException 503 on
     * any other failure -- timeout, connection refused, 5xx -- so the caller can tell "no roadmap"
     * apart from "roadmap-service is down".
     */
    public RoadmapResponseDto fetchMyRoadmap(Long studentId) {
        try {
            return roadmapRestClient.get()
                    .uri(MY_ROADMAP_PATH)
                    .header(AiCoachConstants.USER_ID_HEADER, studentId.toString())
                    .retrieve()
                    .body(RoadmapResponseDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch roadmap for studentId={}: {}", studentId, e.getMessage());
            throw new CustomException("Roadmap service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Forwards the caller's REAL role -- no elevation. GET /api/roadmap/templates requires
     * SUPER_ADMIN or ORG_ADMIN, and the only caller of this method (the refresh endpoint) already
     * requires SUPER_ADMIN in its own service layer before this is ever invoked.
     */
    public List<RoadmapTemplateResponseDto> fetchAllTemplates(String callerRole) {
        try {
            return roadmapRestClient.get()
                    .uri(TEMPLATES_PATH)
                    .header(AiCoachConstants.USER_ROLE_HEADER, callerRole)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to fetch roadmap templates: {}", e.getMessage());
            throw new CustomException("Roadmap service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}

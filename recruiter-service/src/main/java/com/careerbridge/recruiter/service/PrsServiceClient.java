package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fetches the Placement Readiness Score leaderboard, the score half of candidate search and the
 * batch source for org-scoped application listing (prs-service is the only service that holds
 * organizationId per student).
 *
 * A concrete @Service with no interface, matching StudentServiceClient and prs-service's own
 * client pattern.
 */
@Service
public class PrsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PrsServiceClient.class);

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";
    private static final String LEADERBOARD_PATH = "/api/prs/leaderboard";

    /**
     * GET /api/prs/leaderboard permits only SUPER_ADMIN and ORG_ADMIN -- a RECRUITER or
     * PLACEMENT_OFFICER caller of this method would be 403'd by prs-service's own RBAC.
     * recruiter-service has already authorized the caller for candidate search in its own
     * service layer, so this call deliberately elevates to SUPER_ADMIN to read the global
     * leaderboard on their behalf. That elevation never leaves the compose network and is not
     * forwarded to the caller in any response -- only studentId/totalScore/grade come back.
     */
    private static final String ELEVATED_ROLE = "SUPER_ADMIN";

    private final RestClient prsRestClient;

    public PrsServiceClient(@Qualifier("prsRestClient") RestClient prsRestClient) {
        this.prsRestClient = prsRestClient;
    }

    /**
     * Global leaderboard, used by candidate search (a RECRUITER has no organization, so there is
     * no tenant to scope to). Never throws; empty list on any failure -- a prs-service outage
     * must not 500 a candidate search, only drop score data (callers treat a missing studentId
     * as PROFILE_FETCH_FAILED-equivalent: score unavailable).
     */
    public List<PrsLeaderboardEntryDto> fetchGlobalLeaderboard() {
        return fetchLeaderboard(ELEVATED_ROLE, null);
    }

    /**
     * Org-scoped leaderboard for getApplicationsForOrgStudents (PLACEMENT_OFFICER). Elevates to
     * ORG_ADMIN with the caller's own orgId, which prs-service scopes on -- never elevates to
     * SUPER_ADMIN here, since that would leak every organization's students to a caller who is
     * only supposed to see their own.
     */
    public List<PrsLeaderboardEntryDto> fetchOrgLeaderboard(Long orgId) {
        if (orgId == null) {
            log.warn("Cannot fetch an org-scoped leaderboard with no orgId");
            return List.of();
        }
        return fetchLeaderboard("ORG_ADMIN", orgId);
    }

    private List<PrsLeaderboardEntryDto> fetchLeaderboard(String elevatedRole, Long orgId) {
        try {
            RestClient.RequestHeadersSpec<?> request = prsRestClient.get()
                    .uri(LEADERBOARD_PATH)
                    .header(USER_ROLE_HEADER, elevatedRole);

            if (orgId != null) {
                request = request.header(USER_ORG_ID_HEADER, orgId.toString());
            }

            List<PrsLeaderboardEntryDto> leaderboard = request.retrieve()
                    .body(new ParameterizedTypeReference<List<PrsLeaderboardEntryDto>>() {});

            return leaderboard == null ? List.of() : leaderboard;
        } catch (Exception ex) {
            log.warn("Failed to fetch PRS leaderboard: {}", ex.getMessage());
            return List.of();
        }
    }
}

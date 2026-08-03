package com.careerbridge.recruiter.controller;

import com.careerbridge.recruiter.dto.PlacementStatsResponse;
import com.careerbridge.recruiter.service.PlacementStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placement reporting. A fourth controller alongside the application, job and company ones,
 * consistent with how this service already splits by concern. No RBAC here -- it all lives in
 * PlacementStatsService.
 *
 * X-User-Org-Id is required = false, and must stay that way: a SUPER_ADMIN belongs to no
 * organization so the gateway forwards no header at all for them, and marking it required would
 * 400 every SUPER_ADMIN request before the service is reached.
 */
@RestController
@RequestMapping("/api/recruiter/stats")
public class RecruiterStatsController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final PlacementStatsService placementStatsService;

    public RecruiterStatsController(PlacementStatsService placementStatsService) {
        this.placementStatsService = placementStatsService;
    }

    /**
     * ORG_ADMIN or SUPER_ADMIN -- the college placement dashboard.
     *
     * Depends on prs-service for the student roster, and fails closed: an outage returns zeros with
     * totalStudentsInScope = 0 rather than an error or, worse, every organization's numbers.
     */
    @GetMapping("/placement")
    public ResponseEntity<PlacementStatsResponse> getOrgPlacementStats(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(placementStatsService.getOrgPlacementStats(callerRole, callerOrgId));
    }

    /**
     * RECRUITER -- outcomes across the caller's own job postings.
     *
     * Scoped by Job.recruiterId, a local column, so this endpoint makes no cross-service call and
     * keeps answering while prs-service is unavailable.
     */
    @GetMapping("/my")
    public ResponseEntity<PlacementStatsResponse> getMyPlacementStats(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(placementStatsService.getMyPlacementStats(callerRole, recruiterId));
    }
}

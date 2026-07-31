package com.careerbridge.prs.controller;

import com.careerbridge.prs.dto.PrsLeaderboardEntry;
import com.careerbridge.prs.dto.PrsResponse;
import com.careerbridge.prs.service.PrsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity arrives entirely in headers injected by api-gateway after it validates the JWT; this
 * service never parses a token. The gateway strips any client-supplied copy of all three headers,
 * which is what makes them trustworthy here.
 *
 * Consequence: port 8089 must not be publicly reachable. Anyone who can reach it directly can send
 * X-User-Role: SUPER_ADMIN and read every student's placement score. That is a network control, not
 * something this code can enforce.
 *
 * Read-only by design. Every score is derived from events; there is no endpoint that sets one, so a
 * student cannot hand-write their own readiness.
 *
 * Singular /api/prs, matching the other services and the gateway's route predicate. Gateway
 * predicates match whole segments, so a plural mapping would 404 every request arriving through the
 * gateway.
 */
@RestController
@RequestMapping("/api/prs")
public class PrsController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final PrsService prsService;

    public PrsController(PrsService prsService) {
        this.prsService = prsService;
    }

    /** The caller's own score. No role needed -- every student may read their own. */
    @GetMapping("/my")
    public ResponseEntity<PrsResponse> getMyPrs(@RequestHeader(USER_ID_HEADER) Long studentId) {
        return ResponseEntity.ok(prsService.getMyPrs(studentId));
    }

    /**
     * Another student's score. The service refuses anyone who is not an admin; the controller only
     * forwards headers. callerId is passed through so the service can log who read whose score.
     */
    @GetMapping("/{studentId}")
    public ResponseEntity<PrsResponse> getPrsByStudentId(
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long studentId) {
        return ResponseEntity.ok(prsService.getPrsByStudentId(callerId, callerRole, studentId));
    }

    /**
     * X-User-Org-Id is required = false, and must stay that way: a SUPER_ADMIN belongs to no
     * organization, so the gateway forwards no header at all for them. Marking it required would
     * 400 every SUPER_ADMIN request before the service is ever reached -- organization-service's
     * logged lesson. The service ignores it for a SUPER_ADMIN and scopes on it for an ORG_ADMIN.
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<PrsLeaderboardEntry>> getLeaderboard(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(prsService.getLeaderboard(callerRole, callerOrgId));
    }
}

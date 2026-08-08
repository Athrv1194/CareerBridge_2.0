package com.careerbridge.roadmap.controller;

import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.dto.RoadmapTemplateResponse;
import com.careerbridge.roadmap.service.RoadmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity arrives entirely in headers injected by api-gateway after it validates the JWT; this
 * service never parses a token. The gateway strips any client-supplied copy of these headers, which
 * is what makes them trustworthy here.
 *
 * Consequence: port 8088 must not be publicly reachable. Anyone who can reach it directly can send
 * X-User-Id and complete another student's milestones. That is a network control, not something
 * this code can enforce.
 *
 * Singular /api/roadmap, matching the other services and the gateway's route predicate. Gateway
 * predicates match whole segments, so a plural mapping would 404 every request arriving through the
 * gateway.
 *
 * There is no endpoint that creates a roadmap. The only way one comes into existence is the
 * recommendation.generated consumer -- a student cannot ask for a roadmap for a career their
 * assessment did not recommend.
 */
@RestController
@RequestMapping("/api/roadmap")
public class RoadmapController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final RoadmapService roadmapService;

    public RoadmapController(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    /** The caller's own active roadmap. studentId comes from the header, never from a parameter. */
    @GetMapping("/my")
    public ResponseEntity<RoadmapResponse> getMyRoadmap(
            @RequestHeader(USER_ID_HEADER) Long studentId) {
        return ResponseEntity.ok(roadmapService.getMyRoadmap(studentId));
    }

    /**
     * PATCH, not PUT: this flips one field on one milestone rather than replacing the resource.
     * The service refuses a milestone belonging to another student with 403, so the milestoneId in
     * the path is not by itself an authorization decision.
     */
    @PatchMapping("/milestone/{milestoneId}/complete")
    public ResponseEntity<RoadmapResponse> completeMilestone(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @PathVariable Long milestoneId) {
        return ResponseEntity.ok(roadmapService.completeMilestone(studentId, milestoneId));
    }

    /**
     * Admin view of the seeded blueprints. Requiring X-User-Role is also what proves the request
     * came through the gateway authenticated, since the gateway strips the header when there is no
     * valid token. No X-User-Org-Id: templates are global, not owned by a tenant.
     */
    @GetMapping("/templates")
    public ResponseEntity<List<RoadmapTemplateResponse>> getAllTemplates(
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(roadmapService.getAllTemplates(callerRole));
    }
}

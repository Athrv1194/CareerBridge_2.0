package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.PlacementStatsResponse;

public interface PlacementStatsService {

    /**
     * ORG_ADMIN or SUPER_ADMIN. Scoped to the students of callerOrgId, resolved through
     * prs-service -- the only service that holds organizationId per student. SUPER_ADMIN gets the
     * global roster instead.
     *
     * Fails CLOSED: a prs-service outage, an empty college and an ORG_ADMIN with no
     * X-User-Org-Id all yield zeros rather than an error. totalStudentsInScope is what tells the
     * three apart from "nobody was placed".
     */
    PlacementStatsResponse getOrgPlacementStats(String callerRole, Long callerOrgId);

    /**
     * RECRUITER. Scoped to the caller's own jobs via Job.recruiterId, which is a local column --
     * so this path makes NO cross-service call and keeps working when prs-service is down.
     */
    PlacementStatsResponse getMyPlacementStats(String callerRole, Long recruiterId);
}

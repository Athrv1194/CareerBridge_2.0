package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.PlacementStatsResponse;

public interface PlacementStatsService {

    // Fails closed on prs-service outage -- returns zeros, not an error.
    // totalStudentsInScope distinguishes "outage/empty college" from "nobody was placed".
    PlacementStatsResponse getOrgPlacementStats(String callerRole, Long callerOrgId);

    // Scoped to caller's own jobs (local column) -- makes no cross-service call, works when prs-service is down.
    PlacementStatsResponse getMyPlacementStats(String callerRole, Long recruiterId);
}

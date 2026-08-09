package com.careerbridge.roadmap.service;

import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.dto.RoadmapTemplateResponse;

import java.util.List;

public interface RoadmapService {

    /**
     * Materialises a roadmap from the template matching careerName, or returns the student's
     * existing one for that career if they already built it. Idempotent by design: a double-click on
     * "Build my roadmap" must not create a duplicate. 404s if no active template exists for the name.
     */
    RoadmapResponse buildRoadmap(Long studentId, String careerName);

    RoadmapResponse getMyRoadmap(Long studentId);

    /** Every roadmap the student has ever built, active one first (same ordering as getMyRoadmap). */
    List<RoadmapResponse> getMyRoadmaps(Long studentId);

    /**
     * Makes this roadmap the one getMyRoadmap (and therefore the Dashboard) shows, without touching
     * any other roadmap's own progress. 403 if it belongs to another student, matching
     * completeMilestone's ownership-check shape.
     */
    RoadmapResponse activateRoadmap(Long studentId, Long roadmapId);

    RoadmapResponse completeMilestone(Long studentId, Long milestoneId);

    List<RoadmapTemplateResponse> getAllTemplates(String callerRole);
}

package com.careerbridge.roadmap.service;

import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.dto.RoadmapTemplateResponse;
import com.careerbridge.roadmap.event.RecommendationGeneratedEvent;

import java.util.List;

public interface RoadmapService {

    /**
     * Materialises a roadmap from the template matching the event's topCareerName. Idempotent, and
     * fail-soft on a missing template -- called from a RabbitMQ listener, never from a controller.
     */
    void generateRoadmap(RecommendationGeneratedEvent event);

    RoadmapResponse getMyRoadmap(Long studentId);

    RoadmapResponse completeMilestone(Long studentId, Long milestoneId);

    List<RoadmapTemplateResponse> getAllTemplates(String callerRole);
}

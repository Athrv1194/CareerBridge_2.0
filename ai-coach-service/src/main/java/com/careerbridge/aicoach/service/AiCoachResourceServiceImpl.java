package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.MilestoneResourcesResponse;
import com.careerbridge.aicoach.dto.ResourceItemResponse;
import com.careerbridge.aicoach.dto.client.RoadmapResponseDto;
import com.careerbridge.aicoach.dto.client.RoadmapTemplateResponseDto;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.model.MilestoneResourceDocument;
import com.careerbridge.aicoach.model.ResourceItem;
import com.careerbridge.aicoach.repository.MilestoneResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiCoachResourceServiceImpl implements AiCoachResourceService {

    private final RoadmapServiceClient roadmapServiceClient;
    private final MilestoneResourceRepository milestoneResourceRepository;
    private final CatalogRefresher catalogRefresher;

    public AiCoachResourceServiceImpl(RoadmapServiceClient roadmapServiceClient,
                                       MilestoneResourceRepository milestoneResourceRepository,
                                       CatalogRefresher catalogRefresher) {
        this.roadmapServiceClient = roadmapServiceClient;
        this.milestoneResourceRepository = milestoneResourceRepository;
        this.catalogRefresher = catalogRefresher;
    }

    /**
     * Exactly two round trips regardless of milestone count (R7): one GET /api/roadmap/my, one
     * repository query. A per-milestone lookup would be the same N+1 shape recruiter-service
     * explicitly designed away from for its candidate search.
     *
     * []              -> the student has no roadmap yet (R1: fetchMyRoadmap returned null, not 503)
     * [{resources:[]}] -> the roadmap exists but the catalog has not been enriched for this career yet
     */
    @Override
    public List<MilestoneResourcesResponse> getMyResources(String role, Long studentId) {
        if (!AiCoachConstants.ROLE_STUDENT.equals(role)) {
            throw new CustomException("Only STUDENT may view their own resources", HttpStatus.FORBIDDEN);
        }

        RoadmapResponseDto roadmap = roadmapServiceClient.fetchMyRoadmap(studentId);
        if (roadmap == null || roadmap.getMilestones() == null) {
            return List.of();
        }

        List<MilestoneResourceDocument> stored =
                milestoneResourceRepository.findByCareerName(roadmap.getCareerName());
        Map<String, List<ResourceItem>> byTitle = new HashMap<>();
        for (MilestoneResourceDocument doc : stored) {
            byTitle.put(doc.getMilestoneTitle(), doc.getResources());
        }

        List<MilestoneResourcesResponse> result = new ArrayList<>();
        for (RoadmapResponseDto.MilestoneDto milestone : roadmap.getMilestones()) {
            List<ResourceItem> resources = byTitle.getOrDefault(milestone.getTitle(), List.of());
            result.add(MilestoneResourcesResponse.builder()
                    .milestoneTitle(milestone.getTitle())
                    .resources(resources.stream().map(this::toResponse).toList())
                    .build());
        }
        return result;
    }

    /**
     * The GET /api/roadmap/templates fetch runs SYNCHRONOUSLY, before returning 202 -- it is one
     * internal call at a 3s timeout, and an admin getting an immediate 503 when roadmap-service is
     * down is strictly better than a 202 followed by silence. Only the 47-milestone enrichment loop
     * itself is dispatched onto the background thread.
     */
    @Override
    public void refreshCatalog(String role) {
        if (!AiCoachConstants.ROLE_SUPER_ADMIN.equals(role)) {
            throw new CustomException("Only SUPER_ADMIN may trigger a catalog refresh", HttpStatus.FORBIDDEN);
        }

        if (!catalogRefresher.tryStart()) {
            throw new CustomException("A catalog refresh is already running", HttpStatus.CONFLICT);
        }

        try {
            List<RoadmapTemplateResponseDto> templates = roadmapServiceClient.fetchAllTemplates(role);
            catalogRefresher.run(templates);
        } catch (RuntimeException e) {
            // The CAS already claimed the flag; if dispatch never happens (roadmap-service down),
            // release it here rather than leaving every future refresh permanently 409ing.
            catalogRefresher.reset();
            throw e;
        }
    }

    private ResourceItemResponse toResponse(ResourceItem item) {
        return ResourceItemResponse.builder()
                .title(item.getTitle())
                .url(item.getUrl())
                .type(item.getType())
                .platform(item.getPlatform())
                .build();
    }
}

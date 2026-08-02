package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.MilestoneResourcesResponse;

import java.util.List;

public interface AiCoachResourceService {

    List<MilestoneResourcesResponse> getMyResources(String role, Long studentId);

    void refreshCatalog(String role);
}

package com.careerbridge.aicoach.controller;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.MilestoneResourcesResponse;
import com.careerbridge.aicoach.service.AiCoachResourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-coach")
public class AiCoachResourceController {

    private final AiCoachResourceService resourceService;

    public AiCoachResourceController(AiCoachResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    public ResponseEntity<List<MilestoneResourcesResponse>> getResources(
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(resourceService.getMyResources(role, studentId));
    }

    @PostMapping("/resources/refresh")
    public ResponseEntity<Void> refreshResources(
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        resourceService.refreshCatalog(role);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}

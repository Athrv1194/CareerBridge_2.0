package com.careerbridge.recommendation.controller;

import com.careerbridge.recommendation.dto.CareerPathDto;
import com.careerbridge.recommendation.dto.RecommendationResponse;
import com.careerbridge.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * userId arrives in X-User-Id, forwarded by the API Gateway after it validates the JWT -- this
 * service never parses tokens itself.
 *
 * That makes the header a trusted input, so port 8084 must not be publicly reachable: anyone who
 * can hit it directly can set the header to any value and read that student's recommendations.
 * This is a network control (security group / bind address), not something the code can enforce.
 *
 * Every endpoint is a read. Recommendations are created by the RabbitMQ consumer reacting to
 * assessment.completed, never by an HTTP call, so there is no POST here and no request body
 * anywhere in this service.
 */
@RestController
@RequestMapping("/api/recommendation")
public class RecommendationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** The student's current recommendation. 404 until they have completed an assessment. */
    @GetMapping("/my")
    public ResponseEntity<RecommendationResponse> getMyRecommendation(
            @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(recommendationService.getMyRecommendation(userId));
    }

    /** Every recommendation this student has had, newest first. Empty list, not 404, when none. */
    @GetMapping("/history")
    public ResponseEntity<List<RecommendationResponse>> getRecommendationHistory(
            @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(recommendationService.getRecommendationHistory(userId));
    }

    /**
     * The career catalogue behind every ranking. No header: the list is identical for every
     * student.
     *
     * Declared before /{recommendationId} for readability only -- Spring's PathPattern matching
     * already prefers a literal segment over a variable one regardless of declaration order.
     */
    @GetMapping("/careers")
    public ResponseEntity<List<CareerPathDto>> getAllCareers() {
        return ResponseEntity.ok(recommendationService.getAllCareers());
    }

    /** 404 when the recommendation does not exist or belongs to another student. */
    @GetMapping("/{recommendationId}")
    public ResponseEntity<RecommendationResponse> getRecommendationById(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long recommendationId) {
        return ResponseEntity.ok(
                recommendationService.getRecommendationById(userId, recommendationId));
    }
}

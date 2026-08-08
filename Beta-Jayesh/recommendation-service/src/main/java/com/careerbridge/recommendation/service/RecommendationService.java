package com.careerbridge.recommendation.service;

import com.careerbridge.recommendation.dto.CareerPathDto;
import com.careerbridge.recommendation.dto.RecommendationResponse;
import com.careerbridge.recommendation.event.AssessmentCompletedEvent;

import java.util.List;

public interface RecommendationService {

    /**
     * Ranks every career for a completed assessment, supersedes the user's previous active
     * recommendation and publishes recommendation.generated.
     *
     * Caller must have checked idempotency; the unique constraint on assessmentAttemptId is the
     * backstop. Requires userId, attemptId, categoryName and categoryScorePercentage to be non-null.
     */
    RecommendationResponse generateRecommendation(AssessmentCompletedEvent event);

    /** The user's current recommendation. 404 when they have not completed an assessment yet. */
    RecommendationResponse getMyRecommendation(Long userId);

    /** Every recommendation the user has ever had, newest first. Empty list if none. */
    List<RecommendationResponse> getRecommendationHistory(Long userId);

    /** 404 when the recommendation does not exist or belongs to someone else. */
    RecommendationResponse getRecommendationById(Long userId, Long recommendationId);

    /** The career catalogue behind the rankings. Identical for every user, so no userId. */
    List<CareerPathDto> getAllCareers();
}

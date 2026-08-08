package com.careerbridge.recommendation.repository;

import com.careerbridge.recommendation.model.RecommendationReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationReasonRepository extends JpaRepository<RecommendationReason, Long> {

    /** Safe as an Optional: recommendation_id carries a unique constraint, so at most one row. */
    Optional<RecommendationReason> findByRecommendationId(Long recommendationId);
}

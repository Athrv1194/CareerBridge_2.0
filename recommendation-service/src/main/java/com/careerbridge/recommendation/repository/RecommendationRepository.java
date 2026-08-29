package com.careerbridge.recommendation.repository;

import com.careerbridge.recommendation.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // List not Optional: concurrent events can create duplicate active rows; a List degrades
    // (reads take newest, writes deactivate all found) instead of throwing IncorrectResultSize.
    List<Recommendation> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    // Ownership check folded into query -- another user's recommendation is simply not found.
    Optional<Recommendation> findByIdAndUserId(Long id, Long userId);

    // Idempotency fast path for the consumer. Races redelivery; uk_attempt_id is the real guarantee.
    boolean existsByAssessmentAttemptId(Long assessmentAttemptId);
}

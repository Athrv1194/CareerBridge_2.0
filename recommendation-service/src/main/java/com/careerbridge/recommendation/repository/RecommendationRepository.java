package com.careerbridge.recommendation.repository;

import com.careerbridge.recommendation.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /** Backs GET /history -- newest first, active and superseded alike. */
    List<Recommendation> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Returns a List, not an Optional, even though only one row per user should ever be active.
     *
     * Nothing in the schema can enforce "at most one active row per user" -- MySQL has no partial
     * unique index -- and under REPEATABLE READ two concurrent events can both observe no active
     * row and both insert. An Optional finder would then throw
     * IncorrectResultSizeDataAccessException inside generateRecommendation, the consumer's
     * fail-soft catch would swallow it, and that user would be permanently stuck: every later event
     * fails the same way. A List degrades instead of breaking -- reads take the newest, writes
     * deactivate every row they find and so repair the duplicate on the next event.
     */
    List<Recommendation> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * Ownership check folded into the query, so a recommendation belonging to another user is
     * simply not found rather than fetched and then compared.
     */
    Optional<Recommendation> findByIdAndUserId(Long id, Long userId);

    /**
     * Consumer's idempotency fast path. Best-effort only -- it races redelivery -- with the unique
     * constraint on Recommendation.assessmentAttemptId as the actual guarantee.
     */
    boolean existsByAssessmentAttemptId(Long assessmentAttemptId);
}

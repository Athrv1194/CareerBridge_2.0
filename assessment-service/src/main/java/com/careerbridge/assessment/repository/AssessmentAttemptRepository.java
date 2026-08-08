package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.AssessmentAttempt;
import com.careerbridge.assessment.model.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    /**
     * Ownership is part of the lookup, not a check afterwards: another user's attemptId comes back
     * empty and is answered 404, so the endpoint never confirms that the row exists.
     */
    Optional<AssessmentAttempt> findByIdAndUserId(Long id, Long userId);

    List<AssessmentAttempt> findByUserIdOrderByStartedAtDesc(Long userId);

    Optional<AssessmentAttempt> findByUserIdAndCategoryIdAndStatus(Long userId,
                                                                  Long categoryId,
                                                                  AttemptStatus status);

    /**
     * Keyed on section, not categoryId: a retake's random pool pick can land on a different
     * category than an earlier in-progress attempt for the same section, and both must still count
     * as "already in progress" for that section.
     */
    Optional<AssessmentAttempt> findByUserIdAndSectionAndStatus(Long userId,
                                                                String section,
                                                                AttemptStatus status);

    /**
     * Used to find the latest completed Aptitude/Domain Knowledge attempt when Soft Skills (the
     * final section) is submitted, so the published event can average all 3 sections' scores.
     */
    Optional<AssessmentAttempt> findTopByUserIdAndSectionAndStatusOrderByCompletedAtDesc(
            Long userId, String section, AttemptStatus status);
}

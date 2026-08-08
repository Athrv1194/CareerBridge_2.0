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
}

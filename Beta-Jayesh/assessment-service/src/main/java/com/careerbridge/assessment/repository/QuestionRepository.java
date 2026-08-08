package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * ADMIN MODULE: the student-facing finders below all filter isActive.
     *
     * The unfiltered findByCategoryIdOrderByOrderIndex that used to back the student flow is gone
     * rather than left in place -- keeping it would have meant a future caller could silently serve
     * retired questions, and there is no legitimate student-side use for it. The admin listing has
     * its own finder that deliberately includes inactive rows.
     */

    /** Student-facing: active questions in a category, in display order. */
    List<Question> findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(Long categoryId);

    /**
     * Backs the MIN_QUESTIONS_PER_CATEGORY guard in startAttempt, which needs the count only.
     *
     * Filtering isActive here is load-bearing, not cosmetic. maxPossibleScore is a fixed
     * QUESTIONS_PER_ATTEMPT x MAX_OPTION_WEIGHT (15), so if this counted retired questions a
     * category could pass the >= 5 guard, then serve a pool of 3 active ones, and the student would
     * be scored out of 15 with a ceiling of 60% and nothing logged anywhere. The count and the pool
     * must filter identically or they cannot both be right.
     */
    Integer countByCategoryIdAndIsActiveTrue(Long categoryId);

    /**
     * ADMIN MODULE only: every question, including retired ones, grouped by category in display
     * order. This is the one finder that must NOT filter isActive -- an admin cannot reactivate a
     * question they cannot see.
     */
    List<Question> findAllByOrderByCategoryIdAscOrderIndexAsc();
}

package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByCategoryIdOrderByOrderIndex(Long categoryId);

    /** Used by the MIN_QUESTIONS_PER_CATEGORY guard in startAttempt, which needs the count only. */
    Integer countByCategoryId(Long categoryId);
}

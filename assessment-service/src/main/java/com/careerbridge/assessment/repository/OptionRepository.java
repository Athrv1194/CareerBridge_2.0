package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.Option;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptionRepository extends JpaRepository<Option, Long> {

    List<Option> findByQuestionIdOrderByOrderIndex(Long questionId);

    Optional<Option> findByIdAndQuestionId(Long id, Long questionId);

    /**
     * Bulk variant used by submitAttempt and getQuestions: loads every option for a whole category
     * in one query, so answer validation and DTO assembly stay flat instead of one query per
     * question.
     */
    List<Option> findByQuestionIdInOrderByOrderIndex(List<Long> questionIds);
}

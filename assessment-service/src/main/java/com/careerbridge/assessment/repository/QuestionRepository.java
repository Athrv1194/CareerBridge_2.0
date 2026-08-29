package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(Long categoryId);

    // isActive filter is load-bearing: count and pool must filter identically or maxPossibleScore
    // is computed against a larger denominator than the questions actually served.
    Integer countByCategoryIdAndIsActiveTrue(Long categoryId);

    // Admin only -- must NOT filter isActive, or admins can't see retired questions to reactivate them.
    List<Question> findAllByOrderByCategoryIdAscOrderIndexAsc();
}

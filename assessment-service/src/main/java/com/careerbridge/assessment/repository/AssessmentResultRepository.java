package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    Optional<AssessmentResult> findByAttemptId(Long attemptId);

    List<AssessmentResult> findByUserId(Long userId);
}

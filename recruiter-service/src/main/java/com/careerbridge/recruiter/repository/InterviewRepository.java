package com.careerbridge.recruiter.repository;

import com.careerbridge.recruiter.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    /**
     * The recruiter's own interviews, across every application against their jobs. Soonest first.
     *
     * There is deliberately no single-application finder returning an Optional:
     * interviews.application_id carries no unique constraint, since multiple rounds per
     * application are allowed, so a single-result finder would throw
     * IncorrectResultSizeDataAccessException on the second round -- the roadmap-service SEV-2
     * class of bug (a single-result finder needs a unique constraint behind the queried column).
     */
    List<Interview> findByApplicationIdInOrderByScheduledDateAsc(List<Long> applicationIds);
}

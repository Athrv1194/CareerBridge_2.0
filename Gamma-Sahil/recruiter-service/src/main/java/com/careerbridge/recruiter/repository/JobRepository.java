package com.careerbridge.recruiter.repository;

import com.careerbridge.recruiter.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    /** Public browsing -- any authenticated user. */
    List<Job> findByIsActiveTrueOrderByCreatedAtDesc();

    /** The recruiter's own jobs, including inactive ones. */
    List<Job> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    /** Ownership check: prevents a recruiter editing/deleting another recruiter's job. */
    Optional<Job> findByIdAndRecruiterId(Long id, Long recruiterId);
}

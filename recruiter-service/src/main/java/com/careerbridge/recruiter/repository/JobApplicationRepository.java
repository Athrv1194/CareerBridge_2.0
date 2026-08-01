package com.careerbridge.recruiter.repository;

import com.careerbridge.recruiter.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByStudentIdOrderByAppliedAtDesc(Long studentId);

    List<JobApplication> findByJobIdOrderByAppliedAtDesc(Long jobId);

    Optional<JobApplication> findByJobIdAndStudentId(Long jobId, Long studentId);

    /** Fast path for the duplicate-application guard; uk_application_job_student is the real one. */
    boolean existsByJobIdAndStudentId(Long jobId, Long studentId);

    /** Delete guard: a job with existing applications must be deactivated, not deleted. */
    boolean existsByJobId(Long jobId);

    /** Batch source for getApplicationsForOrgStudents -- one query, not N. */
    List<JobApplication> findByStudentIdIn(List<Long> studentIds);

    /** Batch source for getMyInterviews: every application against the recruiter's own jobs. */
    List<JobApplication> findByJobIdIn(List<Long> jobIds);
}

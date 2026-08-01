package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.CreateJobRequest;
import com.careerbridge.recruiter.dto.JobResponse;
import com.careerbridge.recruiter.dto.JobSummaryResponse;
import com.careerbridge.recruiter.dto.UpdateJobRequest;

import java.util.List;

public interface JobService {

    /** RECRUITER only, and only under a company they own. */
    JobResponse createJob(String callerRole, Long recruiterId, CreateJobRequest request);

    /** RECRUITER only, own jobs only. */
    JobResponse updateJob(String callerRole, Long recruiterId, Long jobId, UpdateJobRequest request);

    /** No role restriction: this is what a student reads before applying. */
    JobResponse getJobById(Long jobId);

    /** No role restriction. Active postings only. */
    List<JobSummaryResponse> listActiveJobs();

    /** RECRUITER only. Includes their own inactive jobs, which listActiveJobs hides. */
    List<JobSummaryResponse> listMyJobs(String callerRole, Long recruiterId);

    /** RECRUITER only. Soft close -- the job and its applications survive. */
    void deactivateJob(String callerRole, Long recruiterId, Long jobId);

    /** RECRUITER only. Refused once any application exists; deactivate instead. */
    void deleteJob(String callerRole, Long recruiterId, Long jobId);
}

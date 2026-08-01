package com.careerbridge.recruiter.controller;

import com.careerbridge.recruiter.dto.CreateJobRequest;
import com.careerbridge.recruiter.dto.JobResponse;
import com.careerbridge.recruiter.dto.JobSummaryResponse;
import com.careerbridge.recruiter.dto.UpdateJobRequest;
import com.careerbridge.recruiter.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** No RBAC here: every authorization decision lives in JobService. */
@RestController
@RequestMapping("/api/recruiter/jobs")
public class RecruiterJobController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JobService jobService;

    public RecruiterJobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createJob(callerRole, recruiterId, request));
    }

    /** The job board. Any authenticated user; no identity headers needed. */
    @GetMapping
    public ResponseEntity<List<JobSummaryResponse>> listActiveJobs() {
        return ResponseEntity.ok(jobService.listActiveJobs());
    }

    /** Declared before /{id} so "my" is never parsed as a job id. */
    @GetMapping("/my")
    public ResponseEntity<List<JobSummaryResponse>> listMyJobs(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(jobService.listMyJobs(callerRole, recruiterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> updateJob(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(callerRole, recruiterId, id, request));
    }

    /** 204: the service returns void, and there is no body a caller would read. */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateJob(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        jobService.deactivateJob(callerRole, recruiterId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        jobService.deleteJob(callerRole, recruiterId, id);
        return ResponseEntity.noContent().build();
    }
}

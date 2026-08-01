package com.careerbridge.recruiter.controller;

import com.careerbridge.recruiter.dto.CandidateResponse;
import com.careerbridge.recruiter.dto.InterviewResponse;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.ScheduleInterviewRequest;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;
import com.careerbridge.recruiter.dto.UpdateInterviewRequest;
import com.careerbridge.recruiter.service.ApplicationService;
import com.careerbridge.recruiter.service.CandidateSearchService;
import com.careerbridge.recruiter.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Applications, candidate search and interviews. No RBAC here: every authorization decision lives
 * in ApplicationService, CandidateSearchService or InterviewService.
 *
 * X-User-Org-Id is required = false everywhere, and must stay that way. A SUPER_ADMIN and a
 * RECRUITER both belong to no organization, so the gateway forwards no header at all for them;
 * marking it required would 400 every such request before the service is ever reached. That is
 * organization-service's logged lesson.
 */
@RestController
@RequestMapping("/api/recruiter")
public class RecruiterApplicationController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final ApplicationService applicationService;
    private final CandidateSearchService candidateSearchService;
    private final InterviewService interviewService;

    public RecruiterApplicationController(ApplicationService applicationService,
                                          CandidateSearchService candidateSearchService,
                                          InterviewService interviewService) {
        this.applicationService = applicationService;
        this.candidateSearchService = candidateSearchService;
        this.interviewService = interviewService;
    }

    // -------------------------------------------------------------------------------------------
    // Applications
    // -------------------------------------------------------------------------------------------

    @PostMapping("/jobs/{jobId}/apply")
    public ResponseEntity<JobApplicationResponse> applyToJob(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long jobId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.applyToJob(callerRole, studentId, jobId));
    }

    @GetMapping("/applications/my")
    public ResponseEntity<List<JobApplicationResponse>> getMyApplications(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(applicationService.getMyApplications(callerRole, studentId));
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForJob(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(
                applicationService.getApplicationsForJob(callerRole, recruiterId, jobId));
    }

    /**
     * A null callerOrgId is passed straight through: the service fails closed on it (empty list),
     * never widening to every organization's students.
     */
    @GetMapping("/applications/org")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForOrgStudents(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(
                applicationService.getApplicationsForOrgStudents(callerRole, callerOrgId));
    }

    @PatchMapping("/applications/{applicationId}/status")
    public ResponseEntity<JobApplicationResponse> updateApplicationStatus(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateApplicationStatus(
                callerRole, recruiterId, applicationId, request));
    }

    // -------------------------------------------------------------------------------------------
    // Candidate search
    // -------------------------------------------------------------------------------------------

    /**
     * There is no careerPath filter: the student's top recommended career lives in
     * recommendation-service behind an X-User-Id-keyed endpoint, so supporting it would mean a
     * third REST client and one call per candidate. See CandidateResponse.
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<CandidateResponse>> searchCandidates(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore) {
        return ResponseEntity.ok(
                candidateSearchService.searchCandidates(callerRole, skills, minScore, maxScore));
    }

    // -------------------------------------------------------------------------------------------
    // Interviews
    // -------------------------------------------------------------------------------------------

    @PostMapping("/applications/{applicationId}/interview")
    public ResponseEntity<InterviewResponse> scheduleInterview(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId,
            @Valid @RequestBody ScheduleInterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                interviewService.scheduleInterview(callerRole, recruiterId, applicationId, request));
    }

    @GetMapping("/interviews/my")
    public ResponseEntity<List<InterviewResponse>> getMyInterviews(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(interviewService.getMyInterviews(callerRole, recruiterId));
    }

    @PatchMapping("/interviews/{interviewId}")
    public ResponseEntity<InterviewResponse> updateInterview(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long interviewId,
            @Valid @RequestBody UpdateInterviewRequest request) {
        return ResponseEntity.ok(
                interviewService.updateInterview(callerRole, recruiterId, interviewId, request));
    }
}

package com.careerbridge.recruiter.controller;

import com.careerbridge.recruiter.dto.CandidateResponse;
import com.careerbridge.recruiter.dto.ExtendOfferRequest;
import com.careerbridge.recruiter.dto.InterviewResponse;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.OfferResponseRequest;
import com.careerbridge.recruiter.dto.ResumeFileBlob;
import com.careerbridge.recruiter.dto.ScheduleInterviewRequest;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;
import com.careerbridge.recruiter.dto.UpdateInterviewRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.service.ApplicationService;
import com.careerbridge.recruiter.service.CandidateSearchService;
import com.careerbridge.recruiter.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /** STUDENT, and only for their own application. Replaces any résumé already attached. */
    @PostMapping(value = "/applications/{applicationId}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadApplicationResume(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file) {
        applicationService.uploadResume(callerRole, studentId, applicationId,
                readResumeBytes(file), file.getContentType(), file.getOriginalFilename());
        return ResponseEntity.noContent().build();
    }

    /** The application's owning student, the job's owning recruiter, or an org-view role. */
    @GetMapping("/applications/{applicationId}/resume")
    public ResponseEntity<byte[]> getApplicationResume(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId) {
        ResumeFileBlob blob = applicationService.getResume(callerRole, userId, applicationId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + blob.getFileName() + "\"")
                .body(blob.getBytes());
    }

    // -------------------------------------------------------------------------------------------
    // Offers
    //
    // Two endpoints rather than one, split by who is entitled to decide: the RECRUITER who owns the
    // job extends the offer and names the CTC, and only the STUDENT who owns the application can
    // accept or decline it. Nobody accepts a job on somebody else's behalf.
    // -------------------------------------------------------------------------------------------

    /** RECRUITER, and only for an application against a job they own. */
    @PatchMapping("/applications/{applicationId}/offer")
    public ResponseEntity<JobApplicationResponse> extendOffer(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId,
            @Valid @RequestBody ExtendOfferRequest request) {
        return ResponseEntity.ok(applicationService.extendOffer(
                callerRole, recruiterId, applicationId, request));
    }

    /** STUDENT, and only for their own application. */
    @PatchMapping("/applications/{applicationId}/offer/respond")
    public ResponseEntity<JobApplicationResponse> respondToOffer(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long applicationId,
            @Valid @RequestBody OfferResponseRequest request) {
        return ResponseEntity.ok(applicationService.respondToOffer(
                callerRole, studentId, applicationId, request));
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
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(candidateSearchService.searchCandidates(
                callerRole, skills, minScore, maxScore, department));
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

    private byte[] readResumeBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("No file was uploaded", HttpStatus.BAD_REQUEST);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException("Could not read the uploaded file", HttpStatus.BAD_REQUEST);
        }
    }
}

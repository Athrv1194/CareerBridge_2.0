package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.ExtendOfferRequest;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.OfferResponseRequest;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.ResumeFileBlob;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;
import com.careerbridge.recruiter.event.PlacementCompletedEvent;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.messaging.RecruiterEventPublisher;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.JobApplication;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.OfferOutcome;
import com.careerbridge.recruiter.repository.CompanyRepository;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationServiceImpl.class);

    private static final Set<String> ORG_VIEW_ROLES =
            Set.of(RecruiterRoles.PLACEMENT_OFFICER, RecruiterRoles.ORG_ADMIN, RecruiterRoles.SUPER_ADMIN);

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final PrsServiceClient prsServiceClient;
    private final RecruiterEventPublisher eventPublisher;

    public ApplicationServiceImpl(JobApplicationRepository jobApplicationRepository,
                                  JobRepository jobRepository,
                                  CompanyRepository companyRepository,
                                  PrsServiceClient prsServiceClient,
                                  RecruiterEventPublisher eventPublisher) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.prsServiceClient = prsServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public JobApplicationResponse applyToJob(String callerRole, Long studentId, Long jobId) {
        requireRole(callerRole, RecruiterRoles.STUDENT, "Only a STUDENT may apply to a job");

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        if (Boolean.FALSE.equals(job.getIsActive())) {
            throw new CustomException("This job posting is no longer active", HttpStatus.BAD_REQUEST);
        }

        if (job.getApplicationDeadline() != null && LocalDate.now().isAfter(job.getApplicationDeadline())) {
            throw new CustomException("The application deadline for this job has passed",
                    HttpStatus.BAD_REQUEST);
        }

        // Fast path only. uk_application_job_student is the real guarantee against a double-submit.
        if (jobApplicationRepository.existsByJobIdAndStudentId(jobId, studentId)) {
            throw new CustomException("You have already applied to this job", HttpStatus.BAD_REQUEST);
        }

        JobApplication saved = jobApplicationRepository.save(JobApplication.builder()
                .jobId(jobId)
                .studentId(studentId)
                .build());

        // Fail-soft: the row is already committed, so a broker outage must not report failure for
        // work that happened. Nothing consumes application.submitted yet.
        eventPublisher.publishApplicationSubmitted(jobId, studentId, job.getRecruiterId());

        log.info("studentId={} applied to jobId={} (applicationId={})", studentId, jobId, saved.getId());
        return toResponse(saved, job.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getMyApplications(String callerRole, Long studentId) {
        requireRole(callerRole, RecruiterRoles.STUDENT, "Only a STUDENT may list their own applications");

        return withJobTitles(jobApplicationRepository.findByStudentIdOrderByAppliedAtDesc(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsForJob(String callerRole, Long recruiterId, Long jobId) {
        requireRole(callerRole, RecruiterRoles.RECRUITER,
                "Only a RECRUITER may list the applications for a job");

        Job job = jobRepository.findByIdAndRecruiterId(jobId, recruiterId)
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        return jobApplicationRepository.findByJobIdOrderByAppliedAtDesc(jobId).stream()
                .map(application -> toResponse(application, job.getTitle()))
                .toList();
    }

    /**
     * The student roster for an organization lives only in prs-service, harvested from
     * student.registered -- student-service does not store organizationId at all. So the org
     * scoping is a leaderboard read, not a local query.
     *
     * A prs-service outage yields an empty list rather than a 400 or a 500: the placement officer
     * sees nothing rather than a broken page, and nothing here is a write.
     */
    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsForOrgStudents(String callerRole, Long callerOrgId) {
        if (!ORG_VIEW_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN may list organization applications",
                    HttpStatus.FORBIDDEN);
        }

        List<PrsLeaderboardEntryDto> roster = RecruiterRoles.SUPER_ADMIN.equals(callerRole)
                ? prsServiceClient.fetchGlobalLeaderboard()
                : prsServiceClient.fetchOrgLeaderboard(callerOrgId);

        if (roster.isEmpty()) {
            // Reachable three ways: prs-service down, a genuinely empty organization, or a
            // PLACEMENT_OFFICER whose token carries no X-User-Org-Id. Fail closed in all three --
            // treating a missing org as "show everything" would be a cross-tenant leak.
            log.warn("Empty student roster for callerRole={} callerOrgId={}; returning no applications",
                    callerRole, callerOrgId);
            return List.of();
        }

        List<Long> studentIds = roster.stream()
                .map(PrsLeaderboardEntryDto::getStudentId)
                .filter(Objects::nonNull)
                .toList();

        return withJobTitles(jobApplicationRepository.findByStudentIdIn(studentIds));
    }

    @Override
    @Transactional
    public JobApplicationResponse updateApplicationStatus(String callerRole, Long recruiterId,
                                                          Long applicationId,
                                                          UpdateApplicationStatusRequest request) {
        requireRole(callerRole, RecruiterRoles.RECRUITER,
                "Only a RECRUITER may update an application status");

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));

        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        // Objects.equals, never ==: both are boxed Long well outside the Integer cache, so ==
        // would be false for exactly the ids this check exists to allow.
        if (!Objects.equals(job.getRecruiterId(), recruiterId)) {
            throw new CustomException("You do not own the job this application belongs to",
                    HttpStatus.FORBIDDEN);
        }

        if (application.getStatus() == request.getStatus()) {
            throw new CustomException("Application is already in " + request.getStatus() + " status",
                    HttpStatus.BAD_REQUEST);
        }

        application.setStatus(request.getStatus());
        JobApplication saved = jobApplicationRepository.save(application);

        eventPublisher.publishApplicationStatusUpdated(saved.getId(), saved.getStudentId(),
                saved.getJobId(), saved.getStatus());

        log.info("applicationId={} moved to {} by recruiterId={}",
                applicationId, saved.getStatus(), recruiterId);
        return toResponse(saved, job.getTitle());
    }

    /**
     * The recruiter extends an offer. Sets status to OFFERED and records the CTC and the date.
     *
     * Re-callable while the student has not answered yet -- correcting a mistyped CTC is
     * legitimate, and this deliberately does NOT copy updateApplicationStatus's "already in that
     * status" 400, which is about status transitions rather than offer terms. Once the student has
     * responded the terms are frozen: changing the CTC on an offer somebody already accepted is
     * not a correction, it is a different offer.
     */
    @Override
    @Transactional
    public JobApplicationResponse extendOffer(String callerRole, Long recruiterId, Long applicationId,
                                              ExtendOfferRequest request) {
        requireRole(callerRole, RecruiterRoles.RECRUITER, "Only a RECRUITER may extend an offer");

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));

        Job job = requireOwnedJob(application, recruiterId);

        // A guard updateApplicationStatus does not currently have. Offering a job to a candidate
        // already rejected for it is a data-entry mistake, not a workflow -- un-rejecting is a
        // deliberate status change the recruiter can make first.
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            throw new CustomException("Cannot extend an offer on a rejected application",
                    HttpStatus.BAD_REQUEST);
        }

        if (application.getOfferOutcome() != null) {
            throw new CustomException(
                    "The student has already responded to this offer; its terms can no longer change",
                    HttpStatus.BAD_REQUEST);
        }

        application.setStatus(ApplicationStatus.OFFERED);
        application.setOfferedCtc(request.getOfferedCtc());
        // Preserved on a re-extend: the offer was made when it was first made, and only the amount
        // is being corrected.
        if (application.getOfferDate() == null) {
            application.setOfferDate(LocalDateTime.now());
        }

        JobApplication saved = jobApplicationRepository.save(application);

        // No placement.completed here -- an extended offer is not a placement. See respondToOffer.
        log.info("applicationId={} offered {} LPA by recruiterId={}",
                applicationId, request.getOfferedCtc(), recruiterId);
        return toResponse(saved, job.getTitle());
    }

    /**
     * The student answers. Terminal: ACCEPTED or DECLINED, once.
     *
     * A second response is refused rather than silently overwritten -- accepting and then declining
     * is a renege, which is a real workflow with real consequences and needs to be modelled
     * deliberately if it is ever wanted, not fall out of a mutable field.
     */
    @Override
    @Transactional
    public JobApplicationResponse respondToOffer(String callerRole, Long studentId, Long applicationId,
                                                 OfferResponseRequest request) {
        requireRole(callerRole, RecruiterRoles.STUDENT, "Only a STUDENT may respond to an offer");

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));

        // Objects.equals, never ==: both boxed Long outside the Integer cache. 403 rather than 404
        // here, matching updateApplicationStatus -- the caller addressed a real application, and
        // calling it "not found" would be misleading.
        if (!Objects.equals(application.getStudentId(), studentId)) {
            throw new CustomException("This application does not belong to you", HttpStatus.FORBIDDEN);
        }

        if (application.getStatus() != ApplicationStatus.OFFERED) {
            throw new CustomException("No offer has been extended for this application",
                    HttpStatus.BAD_REQUEST);
        }

        if (application.getOfferOutcome() != null) {
            throw new CustomException(
                    "You have already responded to this offer", HttpStatus.BAD_REQUEST);
        }

        application.setOfferOutcome(request.getOutcome());
        JobApplication saved = jobApplicationRepository.save(application);

        Job job = jobRepository.findById(saved.getJobId()).orElse(null);

        if (request.getOutcome() == OfferOutcome.ACCEPTED) {
            eventPublisher.publishPlacementCompleted(PlacementCompletedEvent.builder()
                    .applicationId(saved.getId())
                    .studentId(saved.getStudentId())
                    .jobId(saved.getJobId())
                    .jobTitle(job == null ? null : job.getTitle())
                    .companyName(companyNameOf(job))
                    .offeredCtc(saved.getOfferedCtc())
                    .offerDate(saved.getOfferDate())
                    .acceptedAt(LocalDateTime.now())
                    .build());
        }

        log.info("applicationId={} offer {} by studentId={}",
                applicationId, request.getOutcome(), studentId);
        return toResponse(saved, job == null ? null : job.getTitle());
    }

    private static final Set<String> ALLOWED_RESUME_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    @Override
    @Transactional
    public void uploadResume(String callerRole, Long studentId, Long applicationId,
                             byte[] bytes, String contentType, String fileName) {
        requireRole(callerRole, RecruiterRoles.STUDENT, "Only a STUDENT may attach a résumé");

        if (bytes == null || bytes.length == 0) {
            throw new CustomException("No file was uploaded", HttpStatus.BAD_REQUEST);
        }
        if (contentType == null || !ALLOWED_RESUME_CONTENT_TYPES.contains(contentType)) {
            throw new CustomException("Only PDF or Word résumés are accepted", HttpStatus.BAD_REQUEST);
        }

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(application.getStudentId(), studentId)) {
            throw new CustomException("This application does not belong to you", HttpStatus.FORBIDDEN);
        }

        application.setResumeFile(bytes);
        application.setResumeFileName(fileName);
        application.setResumeFileContentType(contentType);
        jobApplicationRepository.save(application);

        log.info("applicationId={} received a custom résumé from studentId={}", applicationId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeFileBlob getResume(String callerRole, Long userId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));

        boolean isOwner = RecruiterRoles.STUDENT.equals(callerRole)
                && Objects.equals(application.getStudentId(), userId);
        boolean isRecruiterOwner = RecruiterRoles.RECRUITER.equals(callerRole)
                && jobRepository.findById(application.getJobId())
                        .map(job -> Objects.equals(job.getRecruiterId(), userId))
                        .orElse(false);
        boolean isOrgViewer = ORG_VIEW_ROLES.contains(callerRole);

        if (!isOwner && !isRecruiterOwner && !isOrgViewer) {
            throw new CustomException("You may not view this résumé", HttpStatus.FORBIDDEN);
        }
        if (application.getResumeFile() == null) {
            throw new CustomException("No résumé attached to this application", HttpStatus.NOT_FOUND);
        }

        return ResumeFileBlob.builder()
                .bytes(application.getResumeFile())
                .contentType(application.getResumeFileContentType())
                .fileName(application.getResumeFileName())
                .build();
    }

    /**
     * Loads the job behind an application and asserts the caller owns it. Separate lookup plus
     * Objects.equals rather than a compound finder, deliberately: this returns 403 for someone
     * else's application, where a findByIdAndRecruiterId would collapse it into a 404. CLAUDE.md
     * records why application and interview endpoints differ from company and job lookups here.
     */
    private Job requireOwnedJob(JobApplication application, Long recruiterId) {
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(job.getRecruiterId(), recruiterId)) {
            throw new CustomException("You do not own the job this application belongs to",
                    HttpStatus.FORBIDDEN);
        }
        return job;
    }

    /** Null-safe: the event carries a null company name rather than failing a committed placement. */
    private String companyNameOf(Job job) {
        if (job == null || job.getCompanyId() == null) {
            return null;
        }
        return companyRepository.findById(job.getCompanyId())
                .map(Company::getName)
                .orElse(null);
    }

    private void requireRole(String callerRole, String required, String message) {
        if (!required.equals(callerRole)) {
            throw new CustomException(message, HttpStatus.FORBIDDEN);
        }
    }

    /** One query for every job named in the list, not one per application. */
    private List<JobApplicationResponse> withJobTitles(List<JobApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = applications.stream().map(JobApplication::getJobId).distinct().toList();
        Map<Long, String> titlesById = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Job::getTitle));

        return applications.stream()
                .map(application -> toResponse(application, titlesById.get(application.getJobId())))
                .toList();
    }

    private JobApplicationResponse toResponse(JobApplication application, String jobTitle) {
        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobId(application.getJobId())
                .jobTitle(jobTitle)
                .studentId(application.getStudentId())
                .status(application.getStatus())
                .offeredCtc(application.getOfferedCtc())
                .offerDate(application.getOfferDate())
                .offerOutcome(application.getOfferOutcome())
                .appliedAt(application.getAppliedAt())
                .updatedAt(application.getUpdatedAt())
                .hasResume(application.getResumeFile() != null)
                .build();
    }
}

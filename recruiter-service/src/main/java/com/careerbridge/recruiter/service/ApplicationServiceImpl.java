package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.messaging.RecruiterEventPublisher;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.JobApplication;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final PrsServiceClient prsServiceClient;
    private final RecruiterEventPublisher eventPublisher;

    public ApplicationServiceImpl(JobApplicationRepository jobApplicationRepository,
                                  JobRepository jobRepository,
                                  PrsServiceClient prsServiceClient,
                                  RecruiterEventPublisher eventPublisher) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
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
                .appliedAt(application.getAppliedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}

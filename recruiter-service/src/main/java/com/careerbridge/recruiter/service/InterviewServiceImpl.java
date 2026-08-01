package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.InterviewResponse;
import com.careerbridge.recruiter.dto.ScheduleInterviewRequest;
import com.careerbridge.recruiter.dto.UpdateInterviewRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Interview;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.JobApplication;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.InterviewMode;
import com.careerbridge.recruiter.model.enums.InterviewStatus;
import com.careerbridge.recruiter.repository.InterviewRepository;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewServiceImpl implements InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewServiceImpl.class);

    /**
     * SHORTLISTED is round one. INTERVIEWED is every round after it -- the recruiter moves the
     * applicant there once round one is done, so requiring SHORTLISTED alone would make a second
     * round impossible. APPLIED is excluded deliberately: an interview before any screening is
     * almost always a misclick, and REJECTED/OFFERED are terminal.
     */
    private static final Set<ApplicationStatus> INTERVIEWABLE_STATUSES =
            EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEWED);

    private final InterviewRepository interviewRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;

    public InterviewServiceImpl(InterviewRepository interviewRepository,
                                JobApplicationRepository jobApplicationRepository,
                                JobRepository jobRepository) {
        this.interviewRepository = interviewRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public InterviewResponse scheduleInterview(String callerRole, Long recruiterId, Long applicationId,
                                               ScheduleInterviewRequest request) {
        requireRecruiter(callerRole);

        JobApplication application = requireApplication(applicationId);
        Job job = requireOwnedJob(application.getJobId(), recruiterId);

        if (!INTERVIEWABLE_STATUSES.contains(application.getStatus())) {
            throw new CustomException(
                    "An interview can only be scheduled once the applicant is SHORTLISTED or "
                            + "INTERVIEWED. Current status: " + application.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // Cross-field rule bean validation cannot express without a custom ConstraintValidator;
        // ScheduleInterviewRequest.meetingLink says so.
        if (request.getMode() == InterviewMode.ONLINE
                && (request.getMeetingLink() == null || request.getMeetingLink().isBlank())) {
            throw new CustomException("A meeting link is required for online interviews",
                    HttpStatus.BAD_REQUEST);
        }

        // No "already scheduled" guard, deliberately: multiple rounds per application are
        // supported, so interviews.application_id carries no unique constraint.
        Interview saved = interviewRepository.save(Interview.builder()
                .applicationId(applicationId)
                .scheduledDate(request.getScheduledDate())
                .timeSlot(request.getTimeSlot())
                .mode(request.getMode())
                .meetingLink(request.getMeetingLink())
                .build());

        log.info("Interview {} scheduled for applicationId={} by recruiterId={}",
                saved.getId(), applicationId, recruiterId);
        return toResponse(saved, job.getTitle(), application.getStudentId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewResponse> getMyInterviews(String callerRole, Long recruiterId) {
        requireRecruiter(callerRole);

        List<Job> jobs = jobRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId);
        if (jobs.isEmpty()) {
            return List.of();
        }

        Map<Long, String> jobTitlesById = jobs.stream()
                .collect(Collectors.toMap(Job::getId, Job::getTitle));

        List<JobApplication> applications =
                jobApplicationRepository.findByJobIdIn(List.copyOf(jobTitlesById.keySet()));
        if (applications.isEmpty()) {
            return List.of();
        }

        Map<Long, JobApplication> applicationsById = applications.stream()
                .collect(Collectors.toMap(JobApplication::getId, application -> application));

        return interviewRepository
                .findByApplicationIdInOrderByScheduledDateAsc(List.copyOf(applicationsById.keySet()))
                .stream()
                .map(interview -> {
                    JobApplication application = applicationsById.get(interview.getApplicationId());
                    return toResponse(interview,
                            jobTitlesById.get(application.getJobId()),
                            application.getStudentId());
                })
                .toList();
    }

    @Override
    @Transactional
    public InterviewResponse updateInterview(String callerRole, Long recruiterId, Long interviewId,
                                             UpdateInterviewRequest request) {
        requireRecruiter(callerRole);

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new CustomException("Interview not found", HttpStatus.NOT_FOUND));

        JobApplication application = requireApplication(interview.getApplicationId());
        Job job = requireOwnedJob(application.getJobId(), recruiterId);

        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new CustomException("Cannot update a cancelled interview", HttpStatus.BAD_REQUEST);
        }

        // Null means "leave unchanged", not "clear".
        if (request.getScheduledDate() != null) {
            interview.setScheduledDate(request.getScheduledDate());
        }
        if (request.getTimeSlot() != null) {
            interview.setTimeSlot(request.getTimeSlot());
        }
        if (request.getMode() != null) {
            interview.setMode(request.getMode());
        }
        if (request.getMeetingLink() != null) {
            interview.setMeetingLink(request.getMeetingLink());
        }
        if (request.getStatus() != null) {
            // COMPLETED with no feedback is allowed on purpose: feedback is usually written after
            // the interviewer has had a moment, in a second call to this same endpoint.
            interview.setStatus(request.getStatus());
        }
        if (request.getFeedback() != null) {
            interview.setFeedback(request.getFeedback());
        }

        // Re-checked after the partial update, not only on the request: switching an IN_PERSON
        // interview to ONLINE without supplying a link would otherwise leave it linkless.
        if (interview.getMode() == InterviewMode.ONLINE
                && (interview.getMeetingLink() == null || interview.getMeetingLink().isBlank())) {
            throw new CustomException("A meeting link is required for online interviews",
                    HttpStatus.BAD_REQUEST);
        }

        Interview saved = interviewRepository.save(interview);
        return toResponse(saved, job.getTitle(), application.getStudentId());
    }

    private JobApplication requireApplication(Long applicationId) {
        return jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Loads the job then compares the owner with Objects.equals rather than using
     * findByIdAndRecruiterId, so "someone else's job" is a 403 and not a 404 -- the recruiter did
     * address a real application, and flattening that to "not found" would be misleading.
     * Objects.equals, never ==: both sides are boxed Long outside the Integer cache.
     */
    private Job requireOwnedJob(Long jobId, Long recruiterId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(job.getRecruiterId(), recruiterId)) {
            throw new CustomException("You do not own the job this application belongs to",
                    HttpStatus.FORBIDDEN);
        }

        return job;
    }

    private void requireRecruiter(String callerRole) {
        if (!RecruiterRoles.RECRUITER.equals(callerRole)) {
            throw new CustomException("Only a RECRUITER may manage interviews", HttpStatus.FORBIDDEN);
        }
    }

    private InterviewResponse toResponse(Interview interview, String jobTitle, Long studentId) {
        return InterviewResponse.builder()
                .id(interview.getId())
                .applicationId(interview.getApplicationId())
                .jobTitle(jobTitle)
                .studentId(studentId)
                .scheduledDate(interview.getScheduledDate())
                .timeSlot(interview.getTimeSlot())
                .mode(interview.getMode())
                .meetingLink(interview.getMeetingLink())
                .status(interview.getStatus())
                .feedback(interview.getFeedback())
                .createdAt(interview.getCreatedAt())
                .build();
    }
}

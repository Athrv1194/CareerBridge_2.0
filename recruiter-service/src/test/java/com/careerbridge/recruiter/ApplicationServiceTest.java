package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.messaging.RecruiterEventPublisher;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.JobApplication;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import com.careerbridge.recruiter.service.ApplicationServiceImpl;
import com.careerbridge.recruiter.service.PrsServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Long STUDENT_ID = 42L;
    private static final Long RECRUITER_ID = 7L;
    private static final Long JOB_ID = 100L;
    private static final Long APPLICATION_ID = 500L;

    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobRepository jobRepository;
    @Mock private PrsServiceClient prsServiceClient;
    @Mock private RecruiterEventPublisher eventPublisher;

    @InjectMocks private ApplicationServiceImpl applicationService;

    private Job job;

    @BeforeEach
    void setUp() {
        job = Job.builder()
                .id(JOB_ID)
                .companyId(1L)
                .recruiterId(RECRUITER_ID)
                .title("Java Backend Developer")
                .description("Spring Boot work")
                .isActive(true)
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // applyToJob
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("applyToJob: saves with status APPLIED and publishes application.submitted")
    void applyToJob_Success() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobIdAndStudentId(JOB_ID, STUDENT_ID)).thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class)))
                .thenAnswer(inv -> {
                    JobApplication a = inv.getArgument(0);
                    a.setId(APPLICATION_ID);
                    return a;
                });

        JobApplicationResponse result = applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID);

        assertEquals(ApplicationStatus.APPLIED, result.getStatus());
        assertEquals("Java Backend Developer", result.getJobTitle());
        assertEquals(STUDENT_ID, result.getStudentId());

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(saved.capture());
        assertEquals(ApplicationStatus.APPLIED, saved.getValue().getStatus());

        verify(eventPublisher).publishApplicationSubmitted(JOB_ID, STUDENT_ID, RECRUITER_ID);
    }

    @Test
    @DisplayName("applyToJob: a RECRUITER is refused with 403 before the job is even loaded")
    void applyToJob_RecruiterRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.applyToJob("RECRUITER", STUDENT_ID, JOB_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("applyToJob: unknown job is 404")
    void applyToJob_JobNotFound_Throws404() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyToJob: an inactive job is 400 'no longer active'")
    void applyToJob_InactiveJob_Throws400() {
        job.setIsActive(false);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("no longer active"));
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyToJob: a deadline in the past is 400")
    void applyToJob_DeadlinePassed_Throws400() {
        job.setApplicationDeadline(LocalDate.now().minusDays(1));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("deadline"));
    }

    /**
     * Today's date is the last legal day, not the first illegal one: the check is
     * isAfter(deadline), so a student applying on the deadline itself must still get through.
     */
    @Test
    @DisplayName("applyToJob: a deadline of today still accepts the application")
    void applyToJob_DeadlineToday_Succeeds() {
        job.setApplicationDeadline(LocalDate.now());
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobIdAndStudentId(JOB_ID, STUDENT_ID)).thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse result = applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID);

        assertEquals(ApplicationStatus.APPLIED, result.getStatus());
    }

    @Test
    @DisplayName("applyToJob: applying twice is 400 'already applied'")
    void applyToJob_Duplicate_Throws400() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobIdAndStudentId(JOB_ID, STUDENT_ID)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("already applied"));
        verify(jobApplicationRepository, never()).save(any());
    }

    /**
     * Order is the property that matters: the row must be saved before the event is published, so
     * a consumer can never be told about an application that was never written. The fail-soft half
     * (a broker outage not reaching the caller) lives in RecruiterEventPublisher and is pinned by
     * RecruiterEventPublisherTest -- mocking the publisher to throw here would only test the mock.
     */
    @Test
    @DisplayName("applyToJob: the application is saved before the event is published")
    void applyToJob_SavesBeforePublishing() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobIdAndStudentId(JOB_ID, STUDENT_ID)).thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationService.applyToJob("STUDENT", STUDENT_ID, JOB_ID);

        InOrder inOrder = inOrder(jobApplicationRepository, eventPublisher);
        inOrder.verify(jobApplicationRepository).save(any(JobApplication.class));
        inOrder.verify(eventPublisher).publishApplicationSubmitted(JOB_ID, STUDENT_ID, RECRUITER_ID);
    }

    // ---------------------------------------------------------------------------------------------
    // updateApplicationStatus
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateApplicationStatus: moves the status and publishes application.status.updated")
    void updateStatus_Success() {
        JobApplication application = JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                .status(ApplicationStatus.APPLIED).build();

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse result = applicationService.updateApplicationStatus(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID,
                new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED));

        assertEquals(ApplicationStatus.SHORTLISTED, result.getStatus());
        verify(eventPublisher).publishApplicationStatusUpdated(
                APPLICATION_ID, STUDENT_ID, JOB_ID, ApplicationStatus.SHORTLISTED);
    }

    /**
     * Objects.equals, not ==: 999L and 7L are both boxed Longs outside the Integer cache, so a
     * reference comparison would let every recruiter through here rather than none.
     */
    @Test
    @DisplayName("updateApplicationStatus: another recruiter's job is 403, and nothing is saved")
    void updateStatus_NotOwner_Throws403() {
        JobApplication application = JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                .status(ApplicationStatus.APPLIED).build();

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.updateApplicationStatus("RECRUITER", 999L, APPLICATION_ID,
                        new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobApplicationRepository, never()).save(any());
        verify(eventPublisher, never()).publishApplicationStatusUpdated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateApplicationStatus: setting the status it already holds is 400")
    void updateStatus_AlreadySameStatus_Throws400() {
        JobApplication application = JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                .status(ApplicationStatus.SHORTLISTED).build();

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.updateApplicationStatus("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("already in SHORTLISTED"));
    }

    // ---------------------------------------------------------------------------------------------
    // getApplicationsForOrgStudents
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getApplicationsForOrgStudents: a RECRUITER is refused with 403")
    void getApplicationsForOrg_Recruiter_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.getApplicationsForOrgStudents("RECRUITER", 3L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    /**
     * Fail closed. An empty roster is indistinguishable from "prs-service is down", so widening to
     * every application would turn an outage into a cross-tenant leak.
     */
    @Test
    @DisplayName("getApplicationsForOrgStudents: prs-service down yields an empty list, not everything")
    void getApplicationsForOrg_PrsDown_ReturnsEmpty() {
        when(prsServiceClient.fetchOrgLeaderboard(3L)).thenReturn(List.of());

        List<JobApplicationResponse> result =
                applicationService.getApplicationsForOrgStudents("PLACEMENT_OFFICER", 3L);

        assertTrue(result.isEmpty());
        verify(jobApplicationRepository, never()).findByStudentIdIn(any());
    }

    @Test
    @DisplayName("getApplicationsForOrgStudents: PLACEMENT_OFFICER gets only their org's students")
    void getApplicationsForOrg_ScopesToRoster() {
        when(prsServiceClient.fetchOrgLeaderboard(3L)).thenReturn(List.of(
                PrsLeaderboardEntryDto.builder().studentId(STUDENT_ID).totalScore(70.0).build()));
        when(jobApplicationRepository.findByStudentIdIn(List.of(STUDENT_ID))).thenReturn(List.of(
                JobApplication.builder().id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                        .status(ApplicationStatus.APPLIED).build()));
        when(jobRepository.findAllById(List.of(JOB_ID))).thenReturn(List.of(job));

        List<JobApplicationResponse> result =
                applicationService.getApplicationsForOrgStudents("PLACEMENT_OFFICER", 3L);

        assertEquals(1, result.size());
        assertEquals("Java Backend Developer", result.get(0).getJobTitle());
        verify(prsServiceClient).fetchOrgLeaderboard(3L);
        verify(prsServiceClient, never()).fetchGlobalLeaderboard();
    }

    /** SUPER_ADMIN reads globally rather than being scoped to an org they do not have. */
    @Test
    @DisplayName("getApplicationsForOrgStudents: SUPER_ADMIN reads the global roster")
    void getApplicationsForOrg_SuperAdmin_UsesGlobalLeaderboard() {
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(
                PrsLeaderboardEntryDto.builder().studentId(STUDENT_ID).totalScore(70.0).build()));
        when(jobApplicationRepository.findByStudentIdIn(List.of(STUDENT_ID))).thenReturn(List.of());

        List<JobApplicationResponse> result =
                applicationService.getApplicationsForOrgStudents("SUPER_ADMIN", null);

        assertTrue(result.isEmpty());
        verify(prsServiceClient).fetchGlobalLeaderboard();
        verify(prsServiceClient, never()).fetchOrgLeaderboard(any());
    }

    // ---------------------------------------------------------------------------------------------
    // getMyApplications
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getMyApplications: resolves job titles in one batch query, not one per application")
    void getMyApplications_BatchesJobTitles() {
        when(jobApplicationRepository.findByStudentIdOrderByAppliedAtDesc(STUDENT_ID)).thenReturn(List.of(
                JobApplication.builder().id(1L).jobId(JOB_ID).studentId(STUDENT_ID)
                        .status(ApplicationStatus.APPLIED).build(),
                JobApplication.builder().id(2L).jobId(JOB_ID).studentId(STUDENT_ID)
                        .status(ApplicationStatus.REJECTED).build()));
        when(jobRepository.findAllById(List.of(JOB_ID))).thenReturn(List.of(job));

        List<JobApplicationResponse> result =
                applicationService.getMyApplications("STUDENT", STUDENT_ID);

        assertEquals(2, result.size());
        assertEquals("Java Backend Developer", result.get(0).getJobTitle());
        // Two applications against the same job: one findAllById, never findById per row.
        verify(jobRepository).findAllById(List.of(JOB_ID));
        verify(jobRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("getMyApplications: a RECRUITER is refused with 403")
    void getMyApplications_Recruiter_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.getMyApplications("RECRUITER", STUDENT_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("getApplicationsForJob: another recruiter's job is 404")
    void getApplicationsForJob_NotOwner_Throws404() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, 999L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.getApplicationsForJob("RECRUITER", 999L, JOB_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(jobApplicationRepository, never()).findByJobIdOrderByAppliedAtDesc(anyLong());
    }

    @Test
    @DisplayName("getApplicationsForJob: the owning recruiter sees the applicants")
    void getApplicationsForJob_Owner_ReturnsList() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.findByJobIdOrderByAppliedAtDesc(JOB_ID)).thenReturn(List.of(
                JobApplication.builder().id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                        .status(ApplicationStatus.APPLIED).build()));

        List<JobApplicationResponse> result =
                applicationService.getApplicationsForJob("RECRUITER", RECRUITER_ID, JOB_ID);

        assertEquals(1, result.size());
        assertEquals(STUDENT_ID, result.get(0).getStudentId());
        assertEquals("Java Backend Developer", result.get(0).getJobTitle());
    }
}

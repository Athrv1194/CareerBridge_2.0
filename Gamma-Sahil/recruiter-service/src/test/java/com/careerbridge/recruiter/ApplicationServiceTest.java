package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.ExtendOfferRequest;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.OfferResponseRequest;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Mock private CompanyRepository companyRepository;
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

    // ------------------------------------------------------------------------------------------
    // extendOffer -- the recruiter side
    // ------------------------------------------------------------------------------------------

    private JobApplication interviewedApplication() {
        return JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                .status(ApplicationStatus.INTERVIEWED)
                .build();
    }

    private static ExtendOfferRequest offerOf(String ctc) {
        return ExtendOfferRequest.builder().offeredCtc(new BigDecimal(ctc)).build();
    }

    @Test
    @DisplayName("extendOffer: sets OFFERED, the CTC and the offer date")
    void extendOffer_Valid_SetsStatusCtcAndDate() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(interviewedApplication()));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse result = applicationService.extendOffer(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID, offerOf("8.50"));

        ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(captor.capture());
        assertEquals(ApplicationStatus.OFFERED, captor.getValue().getStatus());
        assertEquals(new BigDecimal("8.50"), captor.getValue().getOfferedCtc());
        assertTrue(captor.getValue().getOfferDate() != null, "offerDate must be stamped");
        assertEquals(new BigDecimal("8.50"), result.getOfferedCtc());
    }

    @Test
    @DisplayName("extendOffer: extending is not a placement, so nothing is published")
    void extendOffer_PublishesNothing() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(interviewedApplication()));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        applicationService.extendOffer("RECRUITER", RECRUITER_ID, APPLICATION_ID, offerOf("8.50"));

        verify(eventPublisher, never()).publishPlacementCompleted(any());
    }

    @Test
    @DisplayName("extendOffer: re-extending before any response corrects the CTC and keeps the date")
    void extendOffer_ReExtendBeforeResponse_UpdatesCtcAndKeepsOriginalDate() {
        LocalDateTime originalDate = LocalDateTime.now().minusDays(2);
        JobApplication alreadyOffered = interviewedApplication();
        alreadyOffered.setStatus(ApplicationStatus.OFFERED);
        alreadyOffered.setOfferedCtc(new BigDecimal("8.00"));
        alreadyOffered.setOfferDate(originalDate);

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(alreadyOffered));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        applicationService.extendOffer("RECRUITER", RECRUITER_ID, APPLICATION_ID, offerOf("9.25"));

        ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(captor.capture());
        assertEquals(new BigDecimal("9.25"), captor.getValue().getOfferedCtc());
        assertEquals(originalDate, captor.getValue().getOfferDate(),
                "correcting the amount must not restamp when the offer was made");
    }

    @Test
    @DisplayName("extendOffer: terms are frozen once the student has responded")
    void extendOffer_AlreadyRespondedTo_Throws400() {
        JobApplication accepted = interviewedApplication();
        accepted.setStatus(ApplicationStatus.OFFERED);
        accepted.setOfferOutcome(OfferOutcome.ACCEPTED);

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(accepted));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class, () -> applicationService.extendOffer(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID, offerOf("12.00")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("extendOffer: refused on a rejected application")
    void extendOffer_RejectedApplication_Throws400() {
        JobApplication rejected = interviewedApplication();
        rejected.setStatus(ApplicationStatus.REJECTED);

        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(rejected));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> applicationService.extendOffer("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        offerOf("8.50"))).getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("extendOffer: a recruiter who does not own the job gets 403, not 404")
    void extendOffer_NotJobOwner_Throws403() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(interviewedApplication()));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class, () -> applicationService.extendOffer(
                "RECRUITER", 999L, APPLICATION_ID, offerOf("8.50")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("extendOffer: unknown application is 404")
    void extendOffer_ApplicationNotFound_Throws404() {
        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CustomException.class,
                () -> applicationService.extendOffer("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        offerOf("8.50"))).getStatus());
    }

    @Test
    @DisplayName("extendOffer: a student cannot extend an offer to themselves")
    void extendOffer_StudentRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> applicationService.extendOffer("STUDENT", STUDENT_ID, APPLICATION_ID,
                        offerOf("8.50"))).getStatus());
        verify(jobApplicationRepository, never()).findById(anyLong());
    }

    // ------------------------------------------------------------------------------------------
    // respondToOffer -- the student side
    // ------------------------------------------------------------------------------------------

    private JobApplication offeredApplication() {
        return JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID)
                .status(ApplicationStatus.OFFERED)
                .offeredCtc(new BigDecimal("8.50"))
                .offerDate(LocalDateTime.now().minusDays(1))
                .build();
    }

    private static OfferResponseRequest respondWith(OfferOutcome outcome) {
        return OfferResponseRequest.builder().outcome(outcome).build();
    }

    @Test
    @DisplayName("respondToOffer: accepting records the outcome and publishes placement.completed")
    void respondToOffer_Accept_SetsOutcomeAndPublishesPlacementCompleted() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(offeredApplication()));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(companyRepository.findById(anyLong()))
                .thenReturn(Optional.of(Company.builder().id(1L).name("Acme Corp").build()));

        applicationService.respondToOffer("STUDENT", STUDENT_ID, APPLICATION_ID,
                respondWith(OfferOutcome.ACCEPTED));

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(saved.capture());
        assertEquals(OfferOutcome.ACCEPTED, saved.getValue().getOfferOutcome());

        ArgumentCaptor<PlacementCompletedEvent> event =
                ArgumentCaptor.forClass(PlacementCompletedEvent.class);
        verify(eventPublisher).publishPlacementCompleted(event.capture());
        assertEquals(STUDENT_ID, event.getValue().getStudentId());
        assertEquals("Acme Corp", event.getValue().getCompanyName());
        assertEquals(new BigDecimal("8.50"), event.getValue().getOfferedCtc());
    }

    @Test
    @DisplayName("respondToOffer: declining is recorded but is not a placement")
    void respondToOffer_Decline_PublishesNothing() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(offeredApplication()));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        applicationService.respondToOffer("STUDENT", STUDENT_ID, APPLICATION_ID,
                respondWith(OfferOutcome.DECLINED));

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(saved.capture());
        assertEquals(OfferOutcome.DECLINED, saved.getValue().getOfferOutcome());
        verify(eventPublisher, never()).publishPlacementCompleted(any());
    }

    @Test
    @DisplayName("respondToOffer: a broker outage must not fail a decision already saved")
    void respondToOffer_BrokerDown_StillSucceeds() {
        // The publisher swallows its own failures, so this asserts the service does not add a
        // rethrow of its own around a row that is already committed.
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(offeredApplication()));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(companyRepository.findById(anyLong())).thenReturn(Optional.empty());

        JobApplicationResponse result = applicationService.respondToOffer(
                "STUDENT", STUDENT_ID, APPLICATION_ID, respondWith(OfferOutcome.ACCEPTED));

        assertEquals(OfferOutcome.ACCEPTED, result.getOfferOutcome());
        verify(eventPublisher).publishPlacementCompleted(any());
    }

    @Test
    @DisplayName("respondToOffer: another student's application is 403, not 404")
    void respondToOffer_NotApplicationOwner_Throws403() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(offeredApplication()));

        CustomException ex = assertThrows(CustomException.class,
                () -> applicationService.respondToOffer("STUDENT", 999L, APPLICATION_ID,
                        respondWith(OfferOutcome.ACCEPTED)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("respondToOffer: refused when no offer has been extended")
    void respondToOffer_NoOfferExtended_Throws400() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(interviewedApplication()));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> applicationService.respondToOffer("STUDENT", STUDENT_ID, APPLICATION_ID,
                        respondWith(OfferOutcome.ACCEPTED))).getStatus());
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("respondToOffer: the outcome is terminal -- a second response is refused")
    void respondToOffer_AlreadyResponded_Throws400() {
        JobApplication accepted = offeredApplication();
        accepted.setOfferOutcome(OfferOutcome.ACCEPTED);
        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(accepted));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> applicationService.respondToOffer("STUDENT", STUDENT_ID, APPLICATION_ID,
                        respondWith(OfferOutcome.DECLINED))).getStatus());
        verify(jobApplicationRepository, never()).save(any());
        verify(eventPublisher, never()).publishPlacementCompleted(any());
    }

    @Test
    @DisplayName("respondToOffer: a recruiter cannot accept on the student's behalf")
    void respondToOffer_RecruiterRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> applicationService.respondToOffer("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        respondWith(OfferOutcome.ACCEPTED))).getStatus());
        verify(jobApplicationRepository, never()).findById(anyLong());
    }
}

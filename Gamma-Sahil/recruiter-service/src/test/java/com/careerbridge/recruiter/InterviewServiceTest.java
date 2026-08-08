package com.careerbridge.recruiter;

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
import com.careerbridge.recruiter.service.InterviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final Long RECRUITER_ID = 7L;
    private static final Long STUDENT_ID = 42L;
    private static final Long JOB_ID = 100L;
    private static final Long APPLICATION_ID = 500L;
    private static final Long INTERVIEW_ID = 900L;

    @Mock private InterviewRepository interviewRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobRepository jobRepository;

    @InjectMocks private InterviewServiceImpl interviewService;

    private Job job;

    @BeforeEach
    void setUp() {
        job = Job.builder()
                .id(JOB_ID).companyId(1L).recruiterId(RECRUITER_ID)
                .title("Java Backend Developer").description("Spring Boot work")
                .isActive(true).build();
    }

    private JobApplication applicationWith(ApplicationStatus status) {
        return JobApplication.builder()
                .id(APPLICATION_ID).jobId(JOB_ID).studentId(STUDENT_ID).status(status).build();
    }

    private ScheduleInterviewRequest onlineRequest() {
        return ScheduleInterviewRequest.builder()
                .scheduledDate(LocalDate.of(2026, 8, 15))
                .timeSlot("10:00 AM - 11:00 AM")
                .mode(InterviewMode.ONLINE)
                .meetingLink("https://meet.example.com/abc-def-ghi")
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // scheduleInterview
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("scheduleInterview: a SHORTLISTED applicant gets round one, status SCHEDULED")
    void scheduleInterview_Shortlisted_Success() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.SHORTLISTED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> {
            Interview i = inv.getArgument(0);
            i.setId(INTERVIEW_ID);
            return i;
        });

        InterviewResponse result = interviewService.scheduleInterview(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID, onlineRequest());

        assertEquals(InterviewStatus.SCHEDULED, result.getStatus());
        assertEquals("Java Backend Developer", result.getJobTitle());
        assertEquals(STUDENT_ID, result.getStudentId());
    }

    /**
     * The pin for the multi-round decision. After round one the recruiter moves the applicant to
     * INTERVIEWED, so a SHORTLISTED-only guard would make round two impossible -- and there is no
     * unique constraint on interviews.application_id to stop a second row. If someone reintroduces
     * a one-interview-per-application rule, this test is what fails.
     */
    @Test
    @DisplayName("scheduleInterview: an INTERVIEWED applicant can be given a second round")
    void scheduleInterview_Round2FromInterviewed_Succeeds() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewResponse result = interviewService.scheduleInterview(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID, onlineRequest());

        assertEquals(InterviewStatus.SCHEDULED, result.getStatus());
        verify(interviewRepository).save(any(Interview.class));
    }

    @Test
    @DisplayName("scheduleInterview: an APPLIED applicant is 400, naming the current status")
    void scheduleInterview_NotShortlisted_Throws400() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.APPLIED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        onlineRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("APPLIED"));
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleInterview: a REJECTED applicant is 400 -- terminal statuses are not interviewable")
    void scheduleInterview_Rejected_Throws400() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.REJECTED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        onlineRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleInterview: ONLINE with no meeting link is 400")
    void scheduleInterview_OnlineWithoutLink_Throws400() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.SHORTLISTED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        ScheduleInterviewRequest noLink = onlineRequest();
        noLink.setMeetingLink("   ");

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("RECRUITER", RECRUITER_ID, APPLICATION_ID, noLink));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("meeting link is required"));
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleInterview: IN_PERSON needs no meeting link")
    void scheduleInterview_InPersonWithoutLink_Succeeds() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.SHORTLISTED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleInterviewRequest inPerson = ScheduleInterviewRequest.builder()
                .scheduledDate(LocalDate.of(2026, 8, 15))
                .timeSlot("2:00 PM - 3:00 PM")
                .mode(InterviewMode.IN_PERSON)
                .build();

        InterviewResponse result = interviewService.scheduleInterview(
                "RECRUITER", RECRUITER_ID, APPLICATION_ID, inPerson);

        assertEquals(InterviewMode.IN_PERSON, result.getMode());
    }

    /**
     * 403 rather than 404, and deliberately so: the recruiter addressed a real application, so
     * flattening this to "not found" would be misleading. Objects.equals, never == -- 999L and 7L
     * are boxed Longs outside the Integer cache.
     */
    @Test
    @DisplayName("scheduleInterview: another recruiter's job is 403")
    void scheduleInterview_NotOwner_Throws403() {
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.SHORTLISTED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("RECRUITER", 999L, APPLICATION_ID, onlineRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleInterview: a STUDENT is refused before anything is loaded")
    void scheduleInterview_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("STUDENT", STUDENT_ID, APPLICATION_ID,
                        onlineRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobApplicationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("scheduleInterview: an unknown application is 404")
    void scheduleInterview_ApplicationNotFound_Throws404() {
        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.scheduleInterview("RECRUITER", RECRUITER_ID, APPLICATION_ID,
                        onlineRequest()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------------------------------------------------------------------------------------------
    // updateInterview
    // ---------------------------------------------------------------------------------------------

    private Interview scheduledInterview() {
        return Interview.builder()
                .id(INTERVIEW_ID).applicationId(APPLICATION_ID)
                .scheduledDate(LocalDate.of(2026, 8, 15)).timeSlot("10:00 AM - 11:00 AM")
                .mode(InterviewMode.ONLINE).meetingLink("https://meet.example.com/abc")
                .status(InterviewStatus.SCHEDULED).build();
    }

    @Test
    @DisplayName("updateInterview: a cancelled interview is immutable")
    void updateInterview_Cancelled_Throws400() {
        Interview cancelled = scheduledInterview();
        cancelled.setStatus(InterviewStatus.CANCELLED);

        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(cancelled));
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.updateInterview("RECRUITER", RECRUITER_ID, INTERVIEW_ID,
                        UpdateInterviewRequest.builder().feedback("late").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(interviewRepository, never()).save(any());
    }

    /** COMPLETED with no feedback is allowed on purpose: feedback usually arrives in a later call. */
    @Test
    @DisplayName("updateInterview: COMPLETED with no feedback is accepted")
    void updateInterview_CompletedWithoutFeedback_Succeeds() {
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(scheduledInterview()));
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewResponse result = interviewService.updateInterview("RECRUITER", RECRUITER_ID, INTERVIEW_ID,
                UpdateInterviewRequest.builder().status(InterviewStatus.COMPLETED).build());

        assertEquals(InterviewStatus.COMPLETED, result.getStatus());
    }

    /**
     * The guard the original spec missed: it validated the meeting link against the REQUEST only,
     * so switching an IN_PERSON interview to ONLINE without supplying one would leave it linkless.
     * This checks the merged entity, after the partial update has been applied.
     */
    @Test
    @DisplayName("updateInterview: switching IN_PERSON to ONLINE without a link is 400")
    void updateInterview_SwitchToOnlineWithoutLink_Throws400() {
        Interview inPerson = scheduledInterview();
        inPerson.setMode(InterviewMode.IN_PERSON);
        inPerson.setMeetingLink(null);

        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(inPerson));
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.updateInterview("RECRUITER", RECRUITER_ID, INTERVIEW_ID,
                        UpdateInterviewRequest.builder().mode(InterviewMode.ONLINE).build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInterview: null fields leave the stored values unchanged")
    void updateInterview_PartialUpdate_LeavesOtherFields() {
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(scheduledInterview()));
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewResponse result = interviewService.updateInterview("RECRUITER", RECRUITER_ID, INTERVIEW_ID,
                UpdateInterviewRequest.builder().timeSlot("3:00 PM - 4:00 PM").build());

        assertEquals("3:00 PM - 4:00 PM", result.getTimeSlot());
        assertEquals(LocalDate.of(2026, 8, 15), result.getScheduledDate());
        assertEquals(InterviewMode.ONLINE, result.getMode());
        assertEquals("https://meet.example.com/abc", result.getMeetingLink());
    }

    @Test
    @DisplayName("updateInterview: an unknown interview is 404")
    void updateInterview_NotFound_Throws404() {
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.updateInterview("RECRUITER", RECRUITER_ID, INTERVIEW_ID,
                        UpdateInterviewRequest.builder().feedback("x").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------------------------------------------------------------------------------------------
    // getMyInterviews
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getMyInterviews: a recruiter with no jobs gets an empty list without further queries")
    void getMyInterviews_NoJobs_ReturnsEmpty() {
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of());

        List<InterviewResponse> result = interviewService.getMyInterviews("RECRUITER", RECRUITER_ID);

        assertTrue(result.isEmpty());
        verify(jobApplicationRepository, never()).findByJobIdIn(any());
        verify(interviewRepository, never()).findByApplicationIdInOrderByScheduledDateAsc(any());
    }

    @Test
    @DisplayName("getMyInterviews: jobs with no applications short-circuit before querying interviews")
    void getMyInterviews_NoApplications_ReturnsEmpty() {
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of(job));
        when(jobApplicationRepository.findByJobIdIn(List.of(JOB_ID))).thenReturn(List.of());

        List<InterviewResponse> result = interviewService.getMyInterviews("RECRUITER", RECRUITER_ID);

        assertTrue(result.isEmpty());
        verify(interviewRepository, never()).findByApplicationIdInOrderByScheduledDateAsc(any());
    }

    /**
     * Every interview returned is looked up in a map built from the same application ids that were
     * queried, so the map lookup can never miss. This pins that the job title and student id are
     * resolved through that chain rather than left null.
     */
    @Test
    @DisplayName("getMyInterviews: resolves job title and studentId through the application chain")
    void getMyInterviews_ResolvesTitleAndStudent() {
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of(job));
        when(jobApplicationRepository.findByJobIdIn(List.of(JOB_ID)))
                .thenReturn(List.of(applicationWith(ApplicationStatus.INTERVIEWED)));
        when(interviewRepository.findByApplicationIdInOrderByScheduledDateAsc(List.of(APPLICATION_ID)))
                .thenReturn(List.of(scheduledInterview()));

        List<InterviewResponse> result = interviewService.getMyInterviews("RECRUITER", RECRUITER_ID);

        assertEquals(1, result.size());
        assertEquals("Java Backend Developer", result.get(0).getJobTitle());
        assertEquals(STUDENT_ID, result.get(0).getStudentId());
    }

    @Test
    @DisplayName("getMyInterviews: a STUDENT is refused with 403")
    void getMyInterviews_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> interviewService.getMyInterviews("STUDENT", STUDENT_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(jobRepository, never()).findByRecruiterIdOrderByCreatedAtDesc(any());
    }
}

package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.CreateJobRequest;
import com.careerbridge.recruiter.dto.JobResponse;
import com.careerbridge.recruiter.dto.JobSummaryResponse;
import com.careerbridge.recruiter.dto.UpdateJobRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import com.careerbridge.recruiter.repository.CompanyRepository;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import com.careerbridge.recruiter.service.JobServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Long RECRUITER_ID = 7L;
    private static final Long COMPANY_ID = 1L;
    private static final Long JOB_ID = 100L;

    @Mock private JobRepository jobRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;

    @InjectMocks private JobServiceImpl jobService;

    private Company company;
    private Job job;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(COMPANY_ID).recruiterId(RECRUITER_ID).name("TechCorp India")
                .industry("IT Services").build();

        job = Job.builder()
                .id(JOB_ID).companyId(COMPANY_ID).recruiterId(RECRUITER_ID)
                .title("Java Backend Developer").description("Spring Boot work")
                .requiredSkills("Java,Spring Boot,PostgreSQL")
                .workMode(WorkMode.HYBRID).jobType(JobType.FULL_TIME)
                .isActive(true).build();
    }

    private CreateJobRequest createRequest() {
        return CreateJobRequest.builder()
                .companyId(COMPANY_ID).title("Java Backend Developer")
                .description("Spring Boot work").requiredSkills("Java,Spring Boot,PostgreSQL")
                .location("Pune").workMode(WorkMode.HYBRID).jobType(JobType.FULL_TIME)
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // createJob
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("createJob: saves under the recruiter's own company, splitting requiredSkills for the response")
    void createJob_Success() {
        when(companyRepository.findByIdAndRecruiterId(COMPANY_ID, RECRUITER_ID))
                .thenReturn(Optional.of(company));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(JOB_ID);
            return j;
        });

        JobResponse result = jobService.createJob("RECRUITER", RECRUITER_ID, createRequest());

        assertEquals("TechCorp India", result.getCompanyName());
        assertEquals(List.of("Java", "Spring Boot", "PostgreSQL"), result.getRequiredSkills());
        assertTrue(result.getIsActive(), "a new job is active by default");

        // recruiterId on the job comes from the caller's header, never from the request body.
        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(saved.capture());
        assertEquals(RECRUITER_ID, saved.getValue().getRecruiterId());
    }

    @Test
    @DisplayName("createJob: a STUDENT is refused with 403 before the company is loaded")
    void createJob_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.createJob("STUDENT", RECRUITER_ID, createRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(companyRepository, never()).findByIdAndRecruiterId(anyLong(), anyLong());
    }

    /**
     * 404, not 403: loaded by (companyId, recruiterId), so posting under another recruiter's
     * company is indistinguishable from a company that does not exist. That is deliberate -- a
     * recruiter has no legitimate reason to address another's company id.
     */
    @Test
    @DisplayName("createJob: another recruiter's company is 404, and nothing is saved")
    void createJob_CompanyNotOwned_Throws404() {
        when(companyRepository.findByIdAndRecruiterId(COMPANY_ID, RECRUITER_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.createJob("RECRUITER", RECRUITER_ID, createRequest()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(jobRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // updateJob
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateJob: null fields leave the stored values unchanged")
    void updateJob_PartialUpdate_LeavesOtherFields() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        JobResponse result = jobService.updateJob("RECRUITER", RECRUITER_ID, JOB_ID,
                UpdateJobRequest.builder().title("Senior Java Developer").build());

        assertEquals("Senior Java Developer", result.getTitle());
        assertEquals("Spring Boot work", result.getDescription());
        assertEquals(WorkMode.HYBRID, result.getWorkMode());
        assertEquals(List.of("Java", "Spring Boot", "PostgreSQL"), result.getRequiredSkills());
    }

    @Test
    @DisplayName("updateJob: another recruiter's job is 404")
    void updateJob_NotOwner_Throws404() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, 999L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.updateJob("RECRUITER", 999L, JOB_ID,
                        UpdateJobRequest.builder().title("x").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(jobRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // deactivateJob
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deactivateJob: flips isActive to false")
    void deactivateJob_Success() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));

        jobService.deactivateJob("RECRUITER", RECRUITER_ID, JOB_ID);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(saved.capture());
        assertFalse(saved.getValue().getIsActive());
    }

    @Test
    @DisplayName("deactivateJob: deactivating twice is 400")
    void deactivateJob_AlreadyInactive_Throws400() {
        job.setIsActive(false);
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));

        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.deactivateJob("RECRUITER", RECRUITER_ID, JOB_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(jobRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // deleteJob
    // ---------------------------------------------------------------------------------------------

    /**
     * The guard exists because applications (and the interviews hanging off them) reference this
     * job id; deleting the row would strand a student's entire application history.
     */
    @Test
    @DisplayName("deleteJob: refused with 400 once any application exists")
    void deleteJob_HasApplications_Throws400() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobId(JOB_ID)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.deleteJob("RECRUITER", RECRUITER_ID, JOB_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("deactivate it instead"));
        verify(jobRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteJob: a job with no applications is deleted")
    void deleteJob_NoApplications_Success() {
        when(jobRepository.findByIdAndRecruiterId(JOB_ID, RECRUITER_ID)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobId(JOB_ID)).thenReturn(false);

        jobService.deleteJob("RECRUITER", RECRUITER_ID, JOB_ID);

        verify(jobRepository).delete(job);
    }

    // ---------------------------------------------------------------------------------------------
    // listing
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("listActiveJobs: resolves company names in one batch query, not one per job")
    void listActiveJobs_BatchesCompanyNames() {
        Job second = Job.builder().id(101L).companyId(COMPANY_ID).recruiterId(RECRUITER_ID)
                .title("Frontend Developer").description("React work").isActive(true).build();

        when(jobRepository.findByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(job, second));
        when(companyRepository.findAllById(List.of(COMPANY_ID))).thenReturn(List.of(company));

        List<JobSummaryResponse> result = jobService.listActiveJobs();

        assertEquals(2, result.size());
        assertEquals("TechCorp India", result.get(0).getCompanyName());
        verify(companyRepository).findAllById(List.of(COMPANY_ID));
        verify(companyRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("listActiveJobs: an empty board short-circuits before querying companies")
    void listActiveJobs_Empty_SkipsCompanyQuery() {
        when(jobRepository.findByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of());

        assertTrue(jobService.listActiveJobs().isEmpty());
        verify(companyRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("listMyJobs: a STUDENT is refused with 403")
    void listMyJobs_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> jobService.listMyJobs("STUDENT", RECRUITER_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("getJobById: an unknown job is 404")
    void getJobById_NotFound_Throws404() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> jobService.getJobById(JOB_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    /** A job whose requiredSkills column is null must yield an empty list, never null. */
    @Test
    @DisplayName("getJobById: null requiredSkills becomes an empty list")
    void getJobById_NullSkills_EmptyList() {
        job.setRequiredSkills(null);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        assertTrue(jobService.getJobById(JOB_ID).getRequiredSkills().isEmpty());
    }
}

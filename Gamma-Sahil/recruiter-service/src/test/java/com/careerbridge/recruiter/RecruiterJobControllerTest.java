package com.careerbridge.recruiter;

import com.careerbridge.recruiter.controller.RecruiterJobController;
import com.careerbridge.recruiter.dto.JobResponse;
import com.careerbridge.recruiter.dto.JobSummaryResponse;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.exception.GlobalExceptionHandler;
import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import com.careerbridge.recruiter.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context, so these run without a database,
 * a broker, student-service or prs-service.
 *
 * Two pieces of wiring are load-bearing. GlobalExceptionHandler is registered explicitly, since
 * standalone setup does not classpath-scan @RestControllerAdvice -- without it a CustomException
 * would surface as a raw 500 and every status assertion here would be meaningless. And a real
 * LocalValidatorFactoryBean is set so @Valid actually fires; standalone setup otherwise skips bean
 * validation entirely and the missing-title test would pass for the wrong reason.
 */
@ExtendWith(MockitoExtension.class)
class RecruiterJobControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock
    private JobService jobService;

    private MockMvc mockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(new RecruiterJobController(jobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/recruiter/jobs returns 200 with the job board")
    void listActiveJobs_Returns200() throws Exception {
        when(jobService.listActiveJobs()).thenReturn(List.of(
                JobSummaryResponse.builder().id(1L).companyId(1L).companyName("TechCorp India")
                        .title("Java Backend Developer").workMode(WorkMode.HYBRID)
                        .jobType(JobType.FULL_TIME).isActive(true).build()));

        mockMvc().perform(get("/api/recruiter/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java Backend Developer"))
                .andExpect(jsonPath("$[0].companyName").value("TechCorp India"));
    }

    @Test
    @DisplayName("POST /api/recruiter/jobs returns 201 with requiredSkills as a list")
    void createJob_ValidBody_Returns201() throws Exception {
        when(jobService.createJob(eq("RECRUITER"), eq(7L), any())).thenReturn(
                JobResponse.builder().id(100L).companyId(1L).companyName("TechCorp India")
                        .recruiterId(7L).title("Java Backend Developer")
                        .requiredSkills(List.of("Java", "Spring Boot"))
                        .workMode(WorkMode.HYBRID).jobType(JobType.FULL_TIME).isActive(true).build());

        mockMvc().perform(post("/api/recruiter/jobs")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":1,"title":"Java Backend Developer",
                                 "description":"Spring Boot work","requiredSkills":"Java,Spring Boot",
                                 "workMode":"HYBRID","jobType":"FULL_TIME"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"));
    }

    /** Proves the real validator is wired: without it this body would reach the service. */
    @Test
    @DisplayName("POST /api/recruiter/jobs with no title returns 400 and never reaches the service")
    void createJob_MissingTitle_Returns400() throws Exception {
        mockMvc().perform(post("/api/recruiter/jobs")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":1,"description":"Spring Boot work",
                                 "workMode":"HYBRID","jobType":"FULL_TIME"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").exists());

        verify(jobService, never()).createJob(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/recruiter/jobs with no workMode returns 400")
    void createJob_MissingWorkMode_Returns400() throws Exception {
        mockMvc().perform(post("/api/recruiter/jobs")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":1,"title":"Java Backend Developer",
                                 "description":"Spring Boot work","jobType":"FULL_TIME"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.workMode").exists());
    }

    @Test
    @DisplayName("PATCH /api/recruiter/jobs/{id}/deactivate returns 204 with no body")
    void deactivateJob_Returns204() throws Exception {
        mockMvc().perform(patch("/api/recruiter/jobs/100/deactivate")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isNoContent());

        verify(jobService).deactivateJob("RECRUITER", 7L, 100L);
    }

    @Test
    @DisplayName("DELETE /api/recruiter/jobs/{id} returns 204")
    void deleteJob_Returns204() throws Exception {
        mockMvc().perform(delete("/api/recruiter/jobs/100")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isNoContent());

        verify(jobService).deleteJob("RECRUITER", 7L, 100L);
    }

    @Test
    @DisplayName("DELETE /api/recruiter/jobs/{id} surfaces the delete guard as 400")
    void deleteJob_HasApplications_Returns400() throws Exception {
        org.mockito.Mockito.doThrow(new CustomException(
                        "Cannot delete a job with existing applications - deactivate it instead",
                        HttpStatus.BAD_REQUEST))
                .when(jobService).deleteJob("RECRUITER", 7L, 100L);

        mockMvc().perform(delete("/api/recruiter/jobs/100")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete a job with existing applications - deactivate it instead"));
    }

    /**
     * /my is a literal segment competing with the /{id} capture on the same prefix. Spring's
     * PathPattern comparator prefers literals, so this must reach listMyJobs -- if precedence ever
     * went the other way, "my" would bind to a Long path variable and 400 instead.
     */
    @Test
    @DisplayName("GET /api/recruiter/jobs/my routes to listMyJobs, not to /{id}")
    void listMyJobs_RoutesCorrectly() throws Exception {
        when(jobService.listMyJobs("RECRUITER", 7L)).thenReturn(List.of());

        mockMvc().perform(get("/api/recruiter/jobs/my")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isOk());

        verify(jobService).listMyJobs("RECRUITER", 7L);
        verify(jobService, never()).getJobById(any());
    }

    /** A missing X-User-Role is MissingRequestHeaderException, which implements ErrorResponse. */
    @Test
    @DisplayName("POST /api/recruiter/jobs with no role header returns 400, not 500")
    void createJob_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(post("/api/recruiter/jobs")
                        .header(USER_ID_HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":1,"title":"Java Backend Developer",
                                 "description":"Spring Boot work","workMode":"HYBRID",
                                 "jobType":"FULL_TIME"}"""))
                .andExpect(status().isBadRequest());
    }

    /** A non-numeric path variable is MethodArgumentTypeMismatchException: 400, never 500. */
    @Test
    @DisplayName("GET /api/recruiter/jobs/{id} with a non-numeric id returns 400, not 500")
    void getJobById_NonNumericId_Returns400() throws Exception {
        mockMvc().perform(get("/api/recruiter/jobs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }
}

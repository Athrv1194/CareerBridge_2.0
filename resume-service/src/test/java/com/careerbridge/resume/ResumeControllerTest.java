package com.careerbridge.resume;

import com.careerbridge.resume.controller.ResumeController;
import com.careerbridge.resume.dto.ResumeDownload;
import com.careerbridge.resume.dto.ResumeResponse;
import com.careerbridge.resume.exception.CustomException;
import com.careerbridge.resume.exception.GlobalExceptionHandler;
import com.careerbridge.resume.service.ResumeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context, so these run without a database,
 * a broker or student-service. GlobalExceptionHandler is registered explicitly, since standalone
 * setup does not classpath-scan @RestControllerAdvice -- without it a CustomException would surface
 * as a raw 500 and every status assertion here would be meaningless.
 *
 * No LocalValidatorFactoryBean here, unlike recruiter-service's controller tests: this controller
 * has no @RequestBody parameter anywhere, so there is nothing for @Valid to fire on.
 */
@ExtendWith(MockitoExtension.class)
class ResumeControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final byte[] PDF_BYTES = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private ResumeService resumeService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new ResumeController(resumeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ResumeResponse sample() {
        return ResumeResponse.builder()
                .id(500L).studentId(42L)
                .fileName("resume_42_v2.pdf")
                .fileUrl("/api/resume/download/500")
                .version(2).atsScore(72.5).isDefault(true)
                .generatedAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/resume/generate returns 201 with the resume metadata")
    void generateResume_Returns201() throws Exception {
        when(resumeService.generateResume("STUDENT", 42L)).thenReturn(sample());

        mockMvc().perform(post("/api/resume/generate")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.atsScore").value(72.5))
                .andExpect(jsonPath("$.fileUrl").value("/api/resume/download/500"));
    }

    @Test
    @DisplayName("POST /api/resume/generate surfaces a student-service outage as 503, not 500")
    void generateResume_StudentServiceDown_Returns503() throws Exception {
        when(resumeService.generateResume("STUDENT", 42L)).thenThrow(new CustomException(
                "Unable to fetch your profile right now - please try again in a moment",
                HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc().perform(post("/api/resume/generate")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("POST /api/resume/generate returns 403 for a RECRUITER")
    void generateResume_WrongRole_Returns403() throws Exception {
        when(resumeService.generateResume("RECRUITER", 42L)).thenThrow(
                new CustomException("Only a STUDENT may generate a resume", HttpStatus.FORBIDDEN));

        mockMvc().perform(post("/api/resume/generate")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/resume/my returns 200 with the caller's resumes")
    void getMyResumes_Returns200() throws Exception {
        when(resumeService.getMyResumes("STUDENT", 42L)).thenReturn(List.of(sample()));

        mockMvc().perform(get("/api/resume/my")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(500))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    /**
     * /my is a literal segment competing with the /{id} capture on the same prefix. Spring's
     * PathPattern comparator prefers literals over captures regardless of declaration order, so
     * this must reach getMyResumes -- if precedence ever went the other way, "my" would bind to a
     * Long path variable and 400. Pinned rather than assumed.
     */
    @Test
    @DisplayName("GET /api/resume/my routes to the list method, not to /{id}")
    void getMyResumes_RoutesCorrectly_NotAsPathVariable() throws Exception {
        when(resumeService.getMyResumes("STUDENT", 42L)).thenReturn(List.of());

        mockMvc().perform(get("/api/resume/my")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());

        verify(resumeService).getMyResumes("STUDENT", 42L);
        verify(resumeService, never()).getResumeById(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /api/resume/{id} returns 200")
    void getResumeById_Returns200() throws Exception {
        when(resumeService.getResumeById("STUDENT", 42L, 500L)).thenReturn(sample());

        mockMvc().perform(get("/api/resume/500")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500));
    }

    /**
     * The check that matters most for this endpoint: the response must be a real PDF body with the
     * right content type, not a JSON envelope. A browser or curl -o writing this to disk is the
     * whole point of the feature.
     */
    @Test
    @DisplayName("GET /api/resume/download/{id} streams PDF bytes with application/pdf and a filename")
    void downloadResume_ReturnsPdfBytes() throws Exception {
        when(resumeService.downloadResume("STUDENT", 42L, 500L)).thenReturn(
                ResumeDownload.builder().fileName("resume_42_v2.pdf").content(PDF_BYTES).build());

        mockMvc().perform(get("/api/resume/download/500")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resume_42_v2.pdf\""))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    @DisplayName("DELETE /api/resume/{id} returns 204 with no body")
    void deleteResume_Returns204() throws Exception {
        mockMvc().perform(delete("/api/resume/500")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isNoContent());

        verify(resumeService).deleteResume("STUDENT", 42L, 500L);
    }

    @Test
    @DisplayName("GET /api/resume/student/{studentId} returns 200 for a RECRUITER")
    void getResumesByStudent_Returns200() throws Exception {
        when(resumeService.getResumesByStudentId("RECRUITER", 42L)).thenReturn(List.of(sample()));

        mockMvc().perform(get("/api/resume/student/42")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(42));
    }

    /** A missing X-User-Role is MissingRequestHeaderException, which does implement ErrorResponse. */
    @Test
    @DisplayName("POST /api/resume/generate with no role header returns 400, not 500")
    void generateResume_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(post("/api/resume/generate").header(USER_ID_HEADER, "42"))
                .andExpect(status().isBadRequest());

        verify(resumeService, never()).generateResume(anyString(), anyLong());
    }

    /** A non-numeric path variable is MethodArgumentTypeMismatchException: 400, never 500. */
    @Test
    @DisplayName("GET /api/resume/{id} with a non-numeric id returns 400, not 500")
    void getResumeById_NonNumericId_Returns400() throws Exception {
        mockMvc().perform(get("/api/resume/abc")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }
}

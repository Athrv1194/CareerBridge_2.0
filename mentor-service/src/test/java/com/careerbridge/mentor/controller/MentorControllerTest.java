package com.careerbridge.mentor.controller;

import com.careerbridge.mentor.dto.MentorProfileResponse;
import com.careerbridge.mentor.dto.MentorshipSessionResponse;
import com.careerbridge.mentor.dto.SessionReviewResponse;
import com.careerbridge.mentor.exception.GlobalExceptionHandler;
import com.careerbridge.mentor.service.MentorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context, so these run without a database or
 * a broker. Two things are wired explicitly because standalone setup does not scan for them --
 * GlobalExceptionHandler (without it a CustomException surfaces as a raw 500 and every status
 * assertion is meaningless) and a real LocalValidatorFactoryBean (without it @Valid does nothing at
 * all and the 400 tests below pass for the wrong reason).
 */
@ExtendWith(MockitoExtension.class)
class MentorControllerTest {

    private static final String H_ID = "X-User-Id";
    private static final String H_ROLE = "X-User-Role";

    @Mock
    private MentorService mentorService;

    private MockMvc mockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(new MentorController(mentorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static MentorProfileResponse profileResponse() {
        return MentorProfileResponse.builder()
                .id(1L).userId(10L).firstName("Raj").lastName("Sharma")
                .currentCompany("Infosys").currentRole("Senior Software Engineer")
                .yearsOfExperience(5)
                .expertiseAreas(List.of("Java", "Spring Boot"))
                .careerPaths(List.of("Backend Developer"))
                .isAvailable(true).sessionsCompleted(0).averageRating(BigDecimal.ZERO)
                .build();
    }

    private static MentorshipSessionResponse sessionResponse(String status) {
        return MentorshipSessionResponse.builder()
                .id(5L).studentId(20L).mentorUserId(10L)
                .mentorProfile(profileResponse())
                .topic("Java backend interviews")
                .scheduledAt(LocalDateTime.now().plusDays(7))
                .durationMinutes(45).status(status)
                .build();
    }

    private static final String VALID_PROFILE_JSON = """
            {"firstName":"Raj","lastName":"Sharma","currentCompany":"Infosys",
             "currentRole":"Senior Software Engineer","yearsOfExperience":5,
             "expertiseAreas":"Java,Spring Boot","careerPaths":"Backend Developer"}
            """;

    @Test
    @DisplayName("POST /profile returns 201 and the expertise areas as an array, not a comma string")
    void createProfile_Returns201() throws Exception {
        when(mentorService.createProfile(anyLong(), anyString(), any())).thenReturn(profileResponse());

        mockMvc().perform(post("/api/mentor/profile")
                        .header(H_ID, "10").header(H_ROLE, "MENTOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PROFILE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expertiseAreas[0]").value("Java"))
                .andExpect(jsonPath("$.averageRating").value(0));
    }

    @Test
    @DisplayName("POST /profile with missing required fields is a 400 and never reaches the service")
    void createProfile_MissingFields_Returns400() throws Exception {
        mockMvc().perform(post("/api/mentor/profile")
                        .header(H_ID, "10").header(H_ROLE, "MENTOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Raj\"}"))
                .andExpect(status().isBadRequest());

        verify(mentorService, never()).createProfile(anyLong(), anyString(), any());
    }

    /** The gateway always injects X-User-Role; its absence is a malformed request, not a 500. */
    @Test
    @DisplayName("a missing X-User-Role is a 400, not a 500")
    void createProfile_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(post("/api/mentor/profile")
                        .header(H_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PROFILE_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a non-numeric X-User-Id is a 400, not a 500")
    void createProfile_NonNumericUserId_Returns400() throws Exception {
        mockMvc().perform(post("/api/mentor/profile")
                        .header(H_ID, "not-a-number").header(H_ROLE, "MENTOR")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PROFILE_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("malformed JSON is a 400, not a 500")
    void createProfile_MalformedJson_Returns400() throws Exception {
        mockMvc().perform(post("/api/mentor/profile")
                        .header(H_ID, "10").header(H_ROLE, "MENTOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /browse returns 200 and passes both filters through")
    void browseMentors_Returns200() throws Exception {
        when(mentorService.browseMentors("Backend Developer", null))
                .thenReturn(List.of(profileResponse()));

        mockMvc().perform(get("/api/mentor/browse")
                        .param("careerPath", "Backend Developer")
                        .header(H_ROLE, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Raj"));

        verify(mentorService).browseMentors("Backend Developer", null);
    }

    @Test
    @DisplayName("POST /sessions returns 201 with status REQUESTED")
    void bookSession_Returns201() throws Exception {
        when(mentorService.bookSession(anyLong(), anyString(), any()))
                .thenReturn(sessionResponse("REQUESTED"));

        String body = """
                {"mentorProfileId":1,"topic":"Java backend interviews",
                 "scheduledAt":"2030-09-15T14:00:00","durationMinutes":45}
                """;

        mockMvc().perform(post("/api/mentor/sessions")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("POST /sessions without a topic is a 400")
    void bookSession_MissingTopic_Returns400() throws Exception {
        String body = """
                {"mentorProfileId":1,"scheduledAt":"2030-09-15T14:00:00"}
                """;

        mockMvc().perform(post("/api/mentor/sessions")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(mentorService, never()).bookSession(anyLong(), anyString(), any());
    }

    /** @Future on scheduledAt -- a session in the past cannot be attended. */
    @Test
    @DisplayName("POST /sessions with a past scheduledAt is a 400")
    void bookSession_PastDate_Returns400() throws Exception {
        String body = """
                {"mentorProfileId":1,"topic":"Java","scheduledAt":"2020-01-01T10:00:00"}
                """;

        mockMvc().perform(post("/api/mentor/sessions")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /sessions/{id}/respond returns 200")
    void respondToSession_Returns200() throws Exception {
        when(mentorService.respondToSession(anyLong(), anyString(), anyLong(), any()))
                .thenReturn(sessionResponse("ACCEPTED"));

        mockMvc().perform(patch("/api/mentor/sessions/5/respond")
                        .header(H_ID, "10").header(H_ROLE, "MENTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\",\"meetingLink\":\"https://meet.google.com/abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("PATCH /sessions/{id}/complete returns 200")
    void completeSession_Returns200() throws Exception {
        when(mentorService.completeSession(anyLong(), anyString(), anyLong()))
                .thenReturn(sessionResponse("COMPLETED"));

        mockMvc().perform(patch("/api/mentor/sessions/5/complete")
                        .header(H_ID, "10").header(H_ROLE, "MENTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /sessions/{id}/cancel returns 200 and forwards the caller's role")
    void cancelSession_Returns200() throws Exception {
        when(mentorService.cancelSession(anyLong(), anyString(), anyLong()))
                .thenReturn(sessionResponse("CANCELLED"));

        mockMvc().perform(patch("/api/mentor/sessions/5/cancel")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT"))
                .andExpect(status().isOk());

        // The role decides which cancellation window applies, so it must reach the service.
        verify(mentorService).cancelSession(20L, "STUDENT", 5L);
    }

    @Test
    @DisplayName("POST /sessions/{id}/review returns 201")
    void createReview_Returns201() throws Exception {
        when(mentorService.createReview(anyLong(), anyString(), anyLong(), any()))
                .thenReturn(SessionReviewResponse.builder()
                        .id(1L).sessionId(5L).studentId(20L).rating(5).comment("Great").build());

        mockMvc().perform(post("/api/mentor/sessions/5/review")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    @DisplayName("a rating outside 1-5 is a 400")
    void createReview_RatingOutOfRange_Returns400() throws Exception {
        mockMvc().perform(post("/api/mentor/sessions/5/review")
                        .header(H_ID, "20").header(H_ROLE, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":9}"))
                .andExpect(status().isBadRequest());

        verify(mentorService, never()).createReview(anyLong(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("GET /profile/{id}/reviews returns 200")
    void getReviewsForMentor_Returns200() throws Exception {
        when(mentorService.getReviewsForMentor(1L)).thenReturn(List.of(
                SessionReviewResponse.builder().id(1L).sessionId(5L).studentId(20L).rating(4).build()));

        mockMvc().perform(get("/api/mentor/profile/1/reviews").header(H_ROLE, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(4));
    }
}

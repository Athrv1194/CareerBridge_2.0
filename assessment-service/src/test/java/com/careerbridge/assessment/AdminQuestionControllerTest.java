package com.careerbridge.assessment;

import com.careerbridge.assessment.controller.AdminQuestionController;
import com.careerbridge.assessment.dto.AdminOptionResponse;
import com.careerbridge.assessment.dto.AdminQuestionResponse;
import com.careerbridge.assessment.exception.CustomException;
import com.careerbridge.assessment.exception.GlobalExceptionHandler;
import com.careerbridge.assessment.service.AdminQuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest, matching every other controller test in this project
 * (organization, roadmap, prs, auth). No Spring context starts, so these run without a database or
 * broker -- @WebMvcTest would boot a web slice and pull assessment-service's datasource
 * autoconfiguration into a test that needs none of it.
 *
 * standaloneSetup still wires a real LocalValidatorFactoryBean, so @Valid genuinely fires here --
 * which is what the two invalid-body tests below depend on.
 *
 * GlobalExceptionHandler is registered explicitly: standalone setup does not pick up
 * @RestControllerAdvice by classpath scanning, and without it a CustomException would surface as a
 * raw 500 and every status assertion would be meaningless.
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionControllerTest {

    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock
    private AdminQuestionService adminQuestionService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AdminQuestionController(adminQuestionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static AdminQuestionResponse sample() {
        return AdminQuestionResponse.builder()
                .id(10L)
                .categoryId(1L)
                .categoryName("Programming Fundamentals")
                .text("What is a closure?")
                .orderIndex(1)
                .isActive(true)
                .options(List.of(
                        AdminOptionResponse.builder().id(1L).text("Correct").weight(3).build(),
                        AdminOptionResponse.builder().id(2L).text("Wrong").weight(0).build()))
                .build();
    }

    /** A body that satisfies every bean-validation constraint on AdminQuestionRequest. */
    private static String validBody() {
        return """
                {
                  "text": "What is a closure?",
                  "categoryId": 1,
                  "orderIndex": 1,
                  "isActive": true,
                  "options": [
                    {"text": "Correct", "weight": 3},
                    {"text": "Wrong", "weight": 0}
                  ]
                }
                """;
    }

    @Test
    @DisplayName("GET /questions returns 200 with the bank")
    void listQuestions_Returns200WithBody() throws Exception {
        when(adminQuestionService.listQuestions(eq("SUPER_ADMIN"), isNull()))
                .thenReturn(List.of(sample()));

        mockMvc().perform(get("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].categoryName").value("Programming Fundamentals"))
                // The admin view exposes weight; the student-facing OptionDto never does.
                .andExpect(jsonPath("$[0].options[0].weight").value(3));
    }

    @Test
    @DisplayName("GET /questions?categoryId= passes the filter through")
    void listQuestions_WithCategoryIdParam_Returns200() throws Exception {
        when(adminQuestionService.listQuestions(eq("ORG_ADMIN"), eq(1L)))
                .thenReturn(List.of(sample()));

        mockMvc().perform(get("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(1));
    }

    @Test
    @DisplayName("GET /questions/{id} returns 200")
    void getQuestion_Returns200() throws Exception {
        when(adminQuestionService.getQuestion(eq("SUPER_ADMIN"), eq(10L))).thenReturn(sample());

        mockMvc().perform(get("/api/assessment/admin/questions/10")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("What is a closure?"));
    }

    @Test
    @DisplayName("POST /questions returns 201 for a valid body")
    void addQuestion_ValidBody_Returns201() throws Exception {
        when(adminQuestionService.addQuestion(eq("SUPER_ADMIN"), any())).thenReturn(sample());

        mockMvc().perform(post("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /questions returns 400 when the question text is missing, before the service runs")
    void addQuestion_InvalidBody_MissingText_Returns400() throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "orderIndex": 1,
                  "options": [
                    {"text": "Correct", "weight": 3},
                    {"text": "Wrong", "weight": 0}
                  ]
                }
                """;

        mockMvc().perform(post("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        // @Valid fires before the controller body executes, so the service is never entered.
        verify(adminQuestionService, never()).addQuestion(any(), any());
    }

    @Test
    @DisplayName("POST /questions returns 400 when fewer than two options are supplied")
    void addQuestion_InvalidBody_TooFewOptions_Returns400() throws Exception {
        // @Size(min = 2): a single-choice question is not a choice.
        String body = """
                {
                  "text": "What is a closure?",
                  "categoryId": 1,
                  "orderIndex": 1,
                  "options": [
                    {"text": "Only one", "weight": 3}
                  ]
                }
                """;

        mockMvc().perform(post("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(adminQuestionService, never()).addQuestion(any(), any());
    }

    @Test
    @DisplayName("PUT /questions/{id} returns 200")
    void editQuestion_Returns200() throws Exception {
        when(adminQuestionService.editQuestion(eq("ORG_ADMIN"), eq(10L), any())).thenReturn(sample());

        mockMvc().perform(put("/api/assessment/admin/questions/10")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("PATCH /questions/{id}/activate returns 204 with no body")
    void activateQuestion_Returns204NoBody() throws Exception {
        mockMvc().perform(patch("/api/assessment/admin/questions/10/activate")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(adminQuestionService).activateQuestion("SUPER_ADMIN", 10L);
    }

    @Test
    @DisplayName("PATCH /questions/{id}/deactivate returns 204 with no body")
    void deactivateQuestion_Returns204NoBody() throws Exception {
        mockMvc().perform(patch("/api/assessment/admin/questions/10/deactivate")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(adminQuestionService).deactivateQuestion("SUPER_ADMIN", 10L);
    }

    @Test
    @DisplayName("a request with no X-User-Role header is rejected before the service is reached")
    void missingRoleHeader_Returns4xx() throws Exception {
        // MissingRequestHeaderException implements ErrorResponse, so GlobalExceptionHandler's
        // catch-all maps it to 400 rather than flattening it into a 500.
        mockMvc().perform(get("/api/assessment/admin/questions"))
                .andExpect(status().is4xxClientError());

        verify(adminQuestionService, never()).listQuestions(any(), any());
    }

    @Test
    @DisplayName("a service 403 surfaces as 403, not 500")
    void unauthorizedRole_Returns403() throws Exception {
        when(adminQuestionService.listQuestions(eq("STUDENT"), isNull()))
                .thenThrow(new CustomException(
                        "Only SUPER_ADMIN or ORG_ADMIN may manage the question bank",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(get("/api/assessment/admin/questions")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}

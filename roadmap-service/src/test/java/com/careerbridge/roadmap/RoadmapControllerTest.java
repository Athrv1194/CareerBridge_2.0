package com.careerbridge.roadmap;

import com.careerbridge.roadmap.controller.RoadmapController;
import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.exception.CustomException;
import com.careerbridge.roadmap.exception.GlobalExceptionHandler;
import com.careerbridge.roadmap.service.RoadmapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context to start, so these run without a
 * database or broker. GlobalExceptionHandler is registered explicitly, since standalone setup does
 * not pick up @RestControllerAdvice by classpath scanning -- without it a CustomException would
 * surface as a raw 500 and the status assertions below would be meaningless.
 */
@ExtendWith(MockitoExtension.class)
class RoadmapControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Mock
    private RoadmapService roadmapService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new RoadmapController(roadmapService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/roadmap builds a roadmap for the requested career and returns 200")
    void buildRoadmap_ValidCareer_Returns200() throws Exception {
        when(roadmapService.buildRoadmap(1L, "Backend Developer")).thenReturn(RoadmapResponse.builder()
                .id(10L).studentId(1L).careerName("Backend Developer")
                .status("IN_PROGRESS").totalMilestones(2).completedMilestones(0)
                .completionPercentage(0.0).milestones(java.util.List.of()).build());

        mockMvc().perform(post("/api/roadmap")
                        .header(USER_ID_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerName\":\"Backend Developer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careerName").value("Backend Developer"));
    }

    @Test
    @DisplayName("POST /api/roadmap with no careerName is a 400")
    void buildRoadmap_BlankCareerName_Returns400() throws Exception {
        mockMvc().perform(post("/api/roadmap")
                        .header(USER_ID_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/roadmap/my returns 200 with the student's roadmap")
    void getMyRoadmap_Found_Returns200() throws Exception {
        when(roadmapService.getMyRoadmap(1L)).thenReturn(RoadmapResponse.builder()
                .id(10L).studentId(1L).careerName("Backend Developer")
                .status("IN_PROGRESS").totalMilestones(2).completedMilestones(0)
                .completionPercentage(0.0).milestones(java.util.List.of()).build());

        mockMvc().perform(get("/api/roadmap/my").header(USER_ID_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.careerName").value("Backend Developer"));
    }

    @Test
    @DisplayName("GET /api/roadmap/my returns 404 when no roadmap exists")
    void getMyRoadmap_NotFound_Returns404() throws Exception {
        when(roadmapService.getMyRoadmap(1L))
                .thenThrow(new CustomException("No active roadmap found", HttpStatus.NOT_FOUND));

        mockMvc().perform(get("/api/roadmap/my").header(USER_ID_HEADER, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No active roadmap found"));
    }

    @Test
    @DisplayName("PATCH /api/roadmap/milestone/{id}/complete returns 200 with updated percentage")
    void completeMilestone_Success_Returns200() throws Exception {
        when(roadmapService.completeMilestone(eq(1L), eq(200L))).thenReturn(RoadmapResponse.builder()
                .id(10L).studentId(1L).completedMilestones(1).completionPercentage(50.0)
                .status("IN_PROGRESS").milestones(java.util.List.of()).build());

        mockMvc().perform(patch("/api/roadmap/milestone/200/complete").header(USER_ID_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedMilestones").value(1))
                .andExpect(jsonPath("$.completionPercentage").value(50.0));
    }

    @Test
    @DisplayName("PATCH /api/roadmap/milestone/{id}/complete returns 403 for another student's milestone")
    void completeMilestone_WrongStudent_Returns403() throws Exception {
        when(roadmapService.completeMilestone(eq(999L), eq(200L)))
                .thenThrow(new CustomException("This milestone does not belong to you", HttpStatus.FORBIDDEN));

        mockMvc().perform(patch("/api/roadmap/milestone/200/complete").header(USER_ID_HEADER, "999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}

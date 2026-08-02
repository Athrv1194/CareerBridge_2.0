package com.careerbridge.aicoach.controller;

import com.careerbridge.aicoach.dto.MilestoneResourcesResponse;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.exception.GlobalExceptionHandler;
import com.careerbridge.aicoach.service.AiCoachResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiCoachResourceControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock
    private AiCoachResourceService resourceService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AiCoachResourceController(resourceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getResources_MissingUserIdHeader_Returns400() throws Exception {
        mockMvc().perform(get("/api/ai-coach/resources").header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResources_NonNumericUserId_Returns400() throws Exception {
        mockMvc().perform(get("/api/ai-coach/resources")
                        .header(USER_ID_HEADER, "abc")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResources_Valid_Returns200() throws Exception {
        when(resourceService.getMyResources("STUDENT", 1L)).thenReturn(List.of(
                MilestoneResourcesResponse.builder().milestoneTitle("A").resources(List.of()).build()));

        mockMvc().perform(get("/api/ai-coach/resources")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_Valid_Returns202() throws Exception {
        mockMvc().perform(post("/api/ai-coach/resources/refresh").header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isAccepted());
    }

    @Test
    void refresh_ServiceThrows409_Returns409() throws Exception {
        doThrow(new CustomException("A catalog refresh is already running", HttpStatus.CONFLICT))
                .when(resourceService).refreshCatalog("SUPER_ADMIN");

        mockMvc().perform(post("/api/ai-coach/resources/refresh").header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isConflict());
    }

    @Test
    void refresh_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(post("/api/ai-coach/resources/refresh"))
                .andExpect(status().isBadRequest());
    }
}

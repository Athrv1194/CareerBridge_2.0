package com.careerbridge.recruiter;

import com.careerbridge.recruiter.controller.RecruiterStatsController;
import com.careerbridge.recruiter.dto.PlacementStatsResponse;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.exception.GlobalExceptionHandler;
import com.careerbridge.recruiter.service.PlacementStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecruiterStatsControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    @Mock private PlacementStatsService placementStatsService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new RecruiterStatsController(placementStatsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static PlacementStatsResponse sample() {
        return PlacementStatsResponse.builder()
                .totalStudentsInScope(40)
                .totalApplications(120)
                .offersExtended(15)
                .offersAccepted(12)
                .offersDeclined(3)
                .placementRate(30.00)
                .averageCtc(new BigDecimal("9.75"))
                .highestCtc(new BigDecimal("18.00"))
                .topCompanies(List.of("Acme Corp", "Globex"))
                .build();
    }

    @Test
    @DisplayName("GET /stats/placement returns 200 with the full aggregate")
    void getOrgStats_Returns200() throws Exception {
        when(placementStatsService.getOrgPlacementStats(anyString(), anyLong())).thenReturn(sample());

        mockMvc().perform(get("/api/recruiter/stats/placement")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudentsInScope").value(40))
                .andExpect(jsonPath("$.offersAccepted").value(12))
                .andExpect(jsonPath("$.placementRate").value(30.00))
                .andExpect(jsonPath("$.topCompanies[0]").value("Acme Corp"));
    }

    @Test
    @DisplayName("GET /stats/placement works with no org header -- SUPER_ADMIN has no organization")
    void getOrgStats_NoOrgHeader_StillReaches200() throws Exception {
        // X-User-Org-Id is required = false on purpose. A SUPER_ADMIN's token carries no org, so
        // marking it required would 400 every SUPER_ADMIN request before the service is reached.
        when(placementStatsService.getOrgPlacementStats(eq("SUPER_ADMIN"), isNull()))
                .thenReturn(sample());

        mockMvc().perform(get("/api/recruiter/stats/placement")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk());

        verify(placementStatsService).getOrgPlacementStats("SUPER_ADMIN", null);
    }

    @Test
    @DisplayName("GET /stats/placement: a missing role header is a 400")
    void getOrgStats_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(get("/api/recruiter/stats/placement")
                        .header(USER_ORG_ID_HEADER, "3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /stats/placement: the service's 403 surfaces as a 403")
    void getOrgStats_StudentRole_Returns403() throws Exception {
        when(placementStatsService.getOrgPlacementStats(anyString(), any()))
                .thenThrow(new CustomException(
                        "Only ORG_ADMIN or SUPER_ADMIN may view organization placement stats",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(get("/api/recruiter/stats/placement")
                        .header(USER_ROLE_HEADER, "STUDENT")
                        .header(USER_ORG_ID_HEADER, "3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /stats/my returns 200 for a recruiter")
    void getMyStats_Returns200() throws Exception {
        when(placementStatsService.getMyPlacementStats("RECRUITER", 7L)).thenReturn(sample());

        mockMvc().perform(get("/api/recruiter/stats/my")
                        .header(USER_ID_HEADER, "7")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offersAccepted").value(12));
    }

    @Test
    @DisplayName("GET /stats/my: a missing X-User-Id is a 400")
    void getMyStats_MissingUserId_Returns400() throws Exception {
        mockMvc().perform(get("/api/recruiter/stats/my")
                        .header(USER_ROLE_HEADER, "RECRUITER"))
                .andExpect(status().isBadRequest());
    }
}

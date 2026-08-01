package com.careerbridge.prs;

import com.careerbridge.prs.controller.PrsController;
import com.careerbridge.prs.dto.PrsBreakdown;
import com.careerbridge.prs.dto.PrsLeaderboardEntry;
import com.careerbridge.prs.dto.PrsResponse;
import com.careerbridge.prs.exception.CustomException;
import com.careerbridge.prs.exception.GlobalExceptionHandler;
import com.careerbridge.prs.service.PrsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context to start, so these run without a
 * database, a broker or student-service. GlobalExceptionHandler is registered explicitly, since
 * standalone setup does not pick up @RestControllerAdvice by classpath scanning -- without it a
 * CustomException would surface as a raw 500 and every status assertion below would be meaningless.
 */
@ExtendWith(MockitoExtension.class)
class PrsControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    @Mock
    private PrsService prsService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new PrsController(prsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static PrsResponse sampleResponse() {
        return PrsResponse.builder()
                .studentId(1L)
                .assessmentScore(90.0)
                .roadmapScore(50.0)
                .profileScore(80.0)
                .totalScore(67.0)
                .grade("B")
                .breakdown(PrsBreakdown.builder()
                        .assessmentWeight(40).assessmentScore(90.0).assessmentContribution(36.0)
                        .roadmapWeight(30).roadmapScore(50.0).roadmapContribution(15.0)
                        .profileWeight(20).profileScore(80.0).profileContribution(16.0)
                        .reservedWeight(10).reservedContribution(0.0)
                        .totalScore(67.0)
                        .build())
                .build();
    }

    @Test
    @DisplayName("GET /api/prs/my returns 200 with the score and its breakdown")
    void getMyPrs_Found_Returns200() throws Exception {
        when(prsService.getMyPrs(1L)).thenReturn(sampleResponse());

        mockMvc().perform(get("/api/prs/my").header(USER_ID_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.totalScore").value(67.0))
                .andExpect(jsonPath("$.grade").value("B"))
                .andExpect(jsonPath("$.breakdown.assessmentWeight").value(40))
                .andExpect(jsonPath("$.breakdown.assessmentContribution").value(36.0))
                .andExpect(jsonPath("$.breakdown.reservedWeight").value(10))
                .andExpect(jsonPath("$.breakdown.reservedContribution").value(0.0));
    }

    @Test
    @DisplayName("GET /api/prs/my returns 404 when the student has no record")
    void getMyPrs_NotFound_Returns404() throws Exception {
        when(prsService.getMyPrs(1L))
                .thenThrow(new CustomException("No placement readiness score found", HttpStatus.NOT_FOUND));

        mockMvc().perform(get("/api/prs/my").header(USER_ID_HEADER, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No placement readiness score found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/prs/{studentId} returns 200 for a SUPER_ADMIN")
    void getPrsByStudentId_SuperAdmin_Returns200() throws Exception {
        when(prsService.getPrsByStudentId(eq(9L), eq("SUPER_ADMIN"), eq(2L)))
                .thenReturn(sampleResponse());

        mockMvc().perform(get("/api/prs/2")
                        .header(USER_ID_HEADER, "9")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(67.0));
    }

    @Test
    @DisplayName("GET /api/prs/{studentId} returns 403 for a STUDENT")
    void getPrsByStudentId_Student_Returns403() throws Exception {
        when(prsService.getPrsByStudentId(eq(9L), eq("STUDENT"), eq(2L)))
                .thenThrow(new CustomException("Only SUPER_ADMIN or ORG_ADMIN may perform this operation",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(get("/api/prs/2")
                        .header(USER_ID_HEADER, "9")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /api/prs/leaderboard returns 200 for an ORG_ADMIN with a ranked list")
    void getLeaderboard_OrgAdmin_Returns200() throws Exception {
        when(prsService.getLeaderboard(eq("ORG_ADMIN"), eq(7L))).thenReturn(List.of(
                PrsLeaderboardEntry.builder().rank(1).studentId(1L).totalScore(80.0).grade("A").build(),
                PrsLeaderboardEntry.builder().rank(2).studentId(2L).totalScore(50.0).grade("C").build()));

        mockMvc().perform(get("/api/prs/leaderboard")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].studentId").value(1))
                .andExpect(jsonPath("$[1].rank").value(2));
    }

    /**
     * The routing check that matters: /leaderboard is a literal segment competing with the
     * /{studentId} pattern on the same prefix. Spring's PathPattern comparator prefers literals over
     * captures, so this must reach getLeaderboard -- if precedence ever went the other way the
     * request would bind "leaderboard" to a Long path variable and 400 instead. Also covers the
     * SUPER_ADMIN shape, where the gateway sends no X-User-Org-Id at all: required = false is what
     * keeps that from 400ing before the service is reached.
     */
    @Test
    @DisplayName("GET /api/prs/leaderboard routes to the leaderboard, not to /{studentId}, with no org header")
    void getLeaderboard_SuperAdminNoOrgHeader_RoutesCorrectly() throws Exception {
        when(prsService.getLeaderboard(eq("SUPER_ADMIN"), isNull())).thenReturn(List.of(
                PrsLeaderboardEntry.builder().rank(1).studentId(3L).totalScore(90.0).grade("A").build()));

        mockMvc().perform(get("/api/prs/leaderboard")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(3));
    }
}

package com.careerbridge.auth;

import com.careerbridge.auth.controller.OrganizationJoinRequestController;
import com.careerbridge.auth.dto.JoinRequestResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.exception.GlobalExceptionHandler;
import com.careerbridge.auth.service.OrganizationJoinRequestService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationJoinRequestControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    @Mock
    private OrganizationJoinRequestService joinRequestService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new OrganizationJoinRequestController(joinRequestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static JoinRequestResponse sample() {
        return JoinRequestResponse.builder().id(1L).userId(5L).firstName("Test").lastName("User")
                .email("test@careerbridge.test").role("STUDENT").organizationId(7L).status("PENDING").build();
    }

    @Test
    @DisplayName("POST /api/auth/me/organization-requests returns 201")
    void submit_Returns201() throws Exception {
        when(joinRequestService.submit(eq(5L), eq("STUDENT"), eq(7L))).thenReturn(sample());

        mockMvc().perform(post("/api/auth/me/organization-requests")
                        .header(USER_ID_HEADER, "5")
                        .header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/auth/me/organization-requests returns 403 for a role that can't join")
    void submit_ForbiddenRole_Returns403() throws Exception {
        when(joinRequestService.submit(eq(5L), eq("RECRUITER"), eq(7L)))
                .thenThrow(new CustomException(
                        "Only students, placement officers and mentors can request to join an organization",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(post("/api/auth/me/organization-requests")
                        .header(USER_ID_HEADER, "5")
                        .header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/auth/admin/organization-requests returns 200 for ORG_ADMIN")
    void listForOrg_Returns200() throws Exception {
        when(joinRequestService.listForOrg(eq("ORG_ADMIN"), eq(7L), isNull())).thenReturn(List.of(sample()));

        mockMvc().perform(get("/api/auth/admin/organization-requests")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("PATCH .../approve returns 200")
    void approve_Returns200() throws Exception {
        JoinRequestResponse approved = sample();
        approved.setStatus("APPROVED");
        when(joinRequestService.approve(eq(1L), eq("ORG_ADMIN"), eq(7L), eq(100L))).thenReturn(approved);

        mockMvc().perform(patch("/api/auth/admin/organization-requests/1/approve")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "7")
                        .header(USER_ID_HEADER, "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PATCH .../approve returns 403 for another organization's admin")
    void approve_WrongOrg_Returns403() throws Exception {
        when(joinRequestService.approve(eq(1L), eq("ORG_ADMIN"), eq(9L), eq(100L)))
                .thenThrow(new CustomException("You do not have access to this request", HttpStatus.FORBIDDEN));

        mockMvc().perform(patch("/api/auth/admin/organization-requests/1/approve")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "9")
                        .header(USER_ID_HEADER, "100"))
                .andExpect(status().isForbidden());
    }
}

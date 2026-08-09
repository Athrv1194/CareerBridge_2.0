package com.careerbridge.organization;

import com.careerbridge.organization.controller.OrganizationRequestController;
import com.careerbridge.organization.dto.OrgRequestResponse;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.exception.GlobalExceptionHandler;
import com.careerbridge.organization.model.RequestStatus;
import com.careerbridge.organization.service.OrganizationRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc, same shape as OrganizationControllerTest. */
@ExtendWith(MockitoExtension.class)
class OrganizationRequestControllerTest {

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Mock
    private OrganizationRequestService organizationRequestService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new OrganizationRequestController(organizationRequestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/organization/apply succeeds with NO role header at all -- the public-path shape")
    void submit_NoHeaders_Returns201() throws Exception {
        when(organizationRequestService.submit(any()))
                .thenReturn(OrgRequestResponse.builder().id(1L).status(RequestStatus.PENDING).build());

        mockMvc().perform(post("/api/organization/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"COEP\",\"institutionCode\":\"COEP\","
                                + "\"contactPersonName\":\"Prof. Sharma\",\"contactEmail\":\"tpo@coep.ac.in\","
                                + "\"contactPhone\":\"+919876543210\",\"organizationType\":\"ENGINEERING_COLLEGE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/organization/requests returns 403 for a non-SUPER_ADMIN caller")
    void list_NotSuperAdmin_Returns403() throws Exception {
        when(organizationRequestService.list(isNull(), eq("STUDENT")))
                .thenThrow(new CustomException("Only SUPER_ADMIN may perform this operation",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(get("/api/organization/requests")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("POST /api/organization/requests/{id}/approve returns 200 with the created organization id")
    void approve_SuperAdmin_Returns200() throws Exception {
        when(organizationRequestService.approve(eq(1L), eq("SUPER_ADMIN"), eq(42L)))
                .thenReturn(OrgRequestResponse.builder()
                        .id(1L).status(RequestStatus.APPROVED).createdOrganizationId(5L).build());

        mockMvc().perform(post("/api/organization/requests/1/approve")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .header(USER_ID_HEADER, "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdOrganizationId").value(5));
    }
}

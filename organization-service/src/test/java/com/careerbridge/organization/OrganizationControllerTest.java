package com.careerbridge.organization;

import com.careerbridge.organization.controller.OrganizationController;
import com.careerbridge.organization.dto.OrganizationResponse;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.exception.GlobalExceptionHandler;
import com.careerbridge.organization.service.OrganizationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OrganizationControllerTest {

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    @Mock
    private OrganizationService organizationService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new OrganizationController(organizationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/organization returns 201 for a SUPER_ADMIN")
    void createOrganization_SuperAdmin_Returns201() throws Exception {
        when(organizationService.createOrganization(any(), eq("SUPER_ADMIN")))
                .thenReturn(OrganizationResponse.builder().id(1L).name("VIT Pune").build());

        mockMvc().perform(post("/api/organization")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VIT Pune\",\"type\":\"ENGINEERING_COLLEGE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("VIT Pune"));
    }

    @Test
    @DisplayName("POST /api/organization returns 403 when the caller is not a SUPER_ADMIN")
    void createOrganization_NotSuperAdmin_Returns403() throws Exception {
        when(organizationService.createOrganization(any(), eq("STUDENT")))
                .thenThrow(new CustomException("Only SUPER_ADMIN may perform this operation",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(post("/api/organization")
                        .header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VIT Pune\",\"type\":\"ENGINEERING_COLLEGE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /api/organization/{id} returns 200 with the organization body")
    void getOrganizationById_Found_Returns200() throws Exception {
        when(organizationService.getOrganizationById(eq(5L), eq("ORG_ADMIN"), eq(5L)))
                .thenReturn(OrganizationResponse.builder()
                        .id(5L).name("COEP").type("ENGINEERING_COLLEGE")
                        .isActive(true).departments(List.of()).build());

        mockMvc().perform(get("/api/organization/5")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("COEP"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("GET /api/organization/{id} returns 404 when the organization does not exist")
    void getOrganizationById_NotFound_Returns404() throws Exception {
        // No X-User-Org-Id header at all -- the SUPER_ADMIN shape. The controller binds it as null
        // rather than 400ing, which is what required = false buys; a required header here would
        // make every SUPER_ADMIN request fail before reaching the service.
        when(organizationService.getOrganizationById(eq(99L), eq("SUPER_ADMIN"), isNull()))
                .thenThrow(new CustomException("Organization not found", HttpStatus.NOT_FOUND));

        mockMvc().perform(get("/api/organization/99")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization not found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}

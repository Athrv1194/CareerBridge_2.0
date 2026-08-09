package com.careerbridge.auth;

import com.careerbridge.auth.controller.AdminUserController;
import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.UserSummaryResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.exception.GlobalExceptionHandler;
import com.careerbridge.auth.service.AdminUserService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context to start, so these run without a
 * database. GlobalExceptionHandler is registered explicitly, since standalone setup does not pick up
 * @RestControllerAdvice by classpath scanning -- without it a CustomException would surface as a raw
 * 500 and every status assertion below would be meaningless.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static UserSummaryResponse sampleUser() {
        return UserSummaryResponse.builder()
                .id(5L)
                .firstName("Test")
                .lastName("User")
                .email("user5@careerbridge.test")
                .role("STUDENT")
                .organizationId(7L)
                .subscriptionPlan("FREE")
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("GET /api/auth/admin/users returns 200 for a SUPER_ADMIN")
    void listUsers_SuperAdmin_Returns200() throws Exception {
        // No X-User-Org-Id header at all -- the SUPER_ADMIN shape. required = false is what makes
        // this bind to null rather than 400 before the service is reached.
        when(adminUserService.listUsers(eq("SUPER_ADMIN"), isNull(), isNull()))
                .thenReturn(List.of(sampleUser()));

        mockMvc().perform(get("/api/auth/admin/users")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].role").value("STUDENT"))
                // The password hash must never appear in a response.
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/admin/users returns 403 for a STUDENT")
    void listUsers_Student_Returns403() throws Exception {
        when(adminUserService.listUsers(eq("STUDENT"), eq(7L), isNull()))
                .thenThrow(new CustomException("Only SUPER_ADMIN or ORG_ADMIN may perform this operation",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(get("/api/auth/admin/users")
                        .header(USER_ROLE_HEADER, "STUDENT")
                        .header(USER_ORG_ID_HEADER, "7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /api/auth/admin/users?role=STUDENT passes the filter through")
    void listUsers_RoleFilter_PassedThrough() throws Exception {
        when(adminUserService.listUsers(eq("ORG_ADMIN"), eq(7L), eq("STUDENT")))
                .thenReturn(List.of(sampleUser()));

        mockMvc().perform(get("/api/auth/admin/users")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .header(USER_ORG_ID_HEADER, "7")
                        .param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("STUDENT"));
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/role returns 200 for a SUPER_ADMIN")
    void changeUserRole_SuperAdmin_Returns200() throws Exception {
        UserSummaryResponse promoted = sampleUser();
        promoted.setRole("MENTOR");
        when(adminUserService.changeUserRole(eq("SUPER_ADMIN"), eq(100L), eq(5L), eq("MENTOR")))
                .thenReturn(promoted);

        mockMvc().perform(patch("/api/auth/admin/users/5/role")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .header(USER_ID_HEADER, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MENTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"));
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/organization returns 200 for a SUPER_ADMIN")
    void linkOrganization_SuperAdmin_Returns200() throws Exception {
        UserSummaryResponse linked = sampleUser();
        linked.setOrganizationId(3L);
        when(adminUserService.linkOrganization(eq("SUPER_ADMIN"), eq(5L), eq(3L)))
                .thenReturn(linked);

        mockMvc().perform(patch("/api/auth/admin/users/5/organization")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(3));
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/organization returns 403 for an ORG_ADMIN")
    void linkOrganization_OrgAdmin_Returns403() throws Exception {
        when(adminUserService.linkOrganization(eq("ORG_ADMIN"), eq(5L), eq(3L)))
                .thenThrow(new CustomException("Only SUPER_ADMIN may perform this operation",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(patch("/api/auth/admin/users/5/organization")
                        .header(USER_ROLE_HEADER, "ORG_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/role returns 400 when the body has no role")
    void changeUserRole_BlankRole_Returns400() throws Exception {
        // @NotBlank on ChangeRoleRequest.role, surfaced by the MethodArgumentNotValidException
        // handler rather than reaching the service at all.
        mockMvc().perform(patch("/api/auth/admin/users/5/role")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .header(USER_ID_HEADER, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/deactivate returns 200")
    void deactivateUser_Returns200() throws Exception {
        UserSummaryResponse deactivated = sampleUser();
        deactivated.setIsDeleted(true);
        when(adminUserService.deactivateUser(eq("SUPER_ADMIN"), eq(100L), isNull(), eq(5L)))
                .thenReturn(deactivated);

        mockMvc().perform(patch("/api/auth/admin/users/5/deactivate")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN")
                        .header(USER_ID_HEADER, "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").value(true));
    }

    @Test
    @DisplayName("PATCH /api/auth/admin/users/{id}/activate returns 200")
    void activateUser_Returns200() throws Exception {
        when(adminUserService.activateUser(eq("SUPER_ADMIN"), isNull(), eq(5L)))
                .thenReturn(sampleUser());

        mockMvc().perform(patch("/api/auth/admin/users/5/activate")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").value(false));
    }

    @Test
    @DisplayName("GET /api/auth/admin/stats returns 200 with the counters")
    void getPlatformStats_Returns200() throws Exception {
        when(adminUserService.getPlatformStats(eq("SUPER_ADMIN"), isNull()))
                .thenReturn(AdminStatsResponse.builder()
                        .totalUsers(10L).totalStudents(6L).totalOrgAdmins(2L).totalRecruiters(1L)
                        .totalMentors(0L).totalPlacementOfficers(0L).totalSuperAdmins(1L)
                        .activeUsers(10L).build());

        mockMvc().perform(get("/api/auth/admin/stats")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.totalStudents").value(6))
                .andExpect(jsonPath("$.activeUsers").value(10));
    }

    @Test
    @DisplayName("a non-numeric {userId} is a 400, not a 500")
    void getUserById_NonNumericId_Returns400() throws Exception {
        // MethodArgumentTypeMismatchException does not implement ErrorResponse, so without an
        // explicit handler this would be reported as a server fault for a plain client typo.
        mockMvc().perform(get("/api/auth/admin/users/abc")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'userId'"));
    }
}

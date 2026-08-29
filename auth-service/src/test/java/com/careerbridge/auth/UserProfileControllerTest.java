package com.careerbridge.auth;

import com.careerbridge.auth.controller.UserProfileController;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new UserProfileController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static UserSummaryResponse sample(String department) {
        return UserSummaryResponse.builder().id(5L).firstName("Test").lastName("Student")
                .email("student@careerbridge.test").role("STUDENT").organizationId(7L)
                .department(department).subscriptionPlan("FREE").isDeleted(false).build();
    }

    @Test
    @DisplayName("GET /api/auth/me returns the caller's own record")
    void getOwnProfile_Returns200() throws Exception {
        when(adminUserService.getOwnProfile(eq(5L))).thenReturn(sample("Computer Science"));

        mockMvc().perform(get("/api/auth/me").header(USER_ID_HEADER, "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Computer Science"));
    }

    @Test
    @DisplayName("PATCH /api/auth/me/department assigns the caller's own department")
    void assignOwnDepartment_Returns200() throws Exception {
        when(adminUserService.assignOwnDepartment(eq(5L), eq("Computer Science")))
                .thenReturn(sample("Computer Science"));

        mockMvc().perform(patch("/api/auth/me/department")
                        .header(USER_ID_HEADER, "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Computer Science\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Computer Science"));
    }

    @Test
    @DisplayName("PATCH /api/auth/me/department returns 400 for a user with no organization")
    void assignOwnDepartment_NoOrganization_Returns400() throws Exception {
        when(adminUserService.assignOwnDepartment(eq(5L), eq("Computer Science")))
                .thenThrow(new CustomException("User does not belong to an organization", HttpStatus.BAD_REQUEST));

        mockMvc().perform(patch("/api/auth/me/department")
                        .header(USER_ID_HEADER, "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Computer Science\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/auth/me/department with a null body department clears it")
    void assignOwnDepartment_Null_Clears() throws Exception {
        when(adminUserService.assignOwnDepartment(eq(5L), isNull())).thenReturn(sample(null));

        mockMvc().perform(patch("/api/auth/me/department")
                        .header(USER_ID_HEADER, "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value(org.hamcrest.Matchers.nullValue()));
    }
}

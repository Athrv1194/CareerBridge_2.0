package com.careerbridge.auth;

import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.UserSummaryResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.UserRepository;
import com.careerbridge.auth.service.AdminUserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito -- no Spring context, no database.
 *
 * The authorization tests are the point of this class. Every admin endpoint trusts an X-User-Role
 * header the gateway injects, so this service is the only thing standing between an ORG_ADMIN and
 * another college's users, and between any authenticated caller and the role-change endpoint.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ORG_ADMIN = "ORG_ADMIN";
    private static final String STUDENT = "STUDENT";

    private static final Long CALLER_ID = 100L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private static User user(Long id, Role role, Long orgId, boolean deleted) {
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email("user" + id + "@careerbridge.test")
                .password("hashed-password")
                .role(role)
                .organizationId(orgId)
                .subscriptionPlan("FREE")
                .isDeleted(deleted)
                .build();
    }

    // -------------------------------------------------------------------------------------------
    // listUsers
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN lists every active user on the platform")
    void listUsers_SuperAdmin_ReturnsAllUsers() {
        when(userRepository.findByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(user(1L, Role.STUDENT, 7L, false), user(2L, Role.ORG_ADMIN, 8L, false)));

        List<UserSummaryResponse> result = adminUserService.listUsers(SUPER_ADMIN, null, null);

        assertEquals(2, result.size());
        verify(userRepository, never()).findByOrganizationIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("ORG_ADMIN lists only their own organization's users")
    void listUsers_OrgAdmin_ReturnsOwnOrgOnlyUsers() {
        when(userRepository.findByOrganizationIdAndIsDeletedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(user(1L, Role.STUDENT, 7L, false)));

        List<UserSummaryResponse> result = adminUserService.listUsers(ORG_ADMIN, 7L, null);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getOrganizationId());
        // The global finder must never be touched -- that is the cross-tenant leak this prevents.
        verify(userRepository, never()).findByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("a STUDENT cannot list users, and no query runs")
    void listUsers_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.listUsers(STUDENT, 7L, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).findByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("an ORG_ADMIN with no organization header gets an empty list, never everyone's")
    void listUsers_OrgAdminWithNoOrg_ReturnsEmptyNotGlobal() {
        List<UserSummaryResponse> result = adminUserService.listUsers(ORG_ADMIN, null, null);

        assertTrue(result.isEmpty());
        verify(userRepository, never()).findByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("?role= narrows the list through the role-filtered finder")
    void listUsers_RoleFilter_UsesFilteredFinder() {
        when(userRepository.findByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.STUDENT))
                .thenReturn(List.of(user(1L, Role.STUDENT, 7L, false)));

        List<UserSummaryResponse> result = adminUserService.listUsers(ORG_ADMIN, 7L, "STUDENT");

        assertEquals(1, result.size());
        assertEquals("STUDENT", result.get(0).getRole());
        verify(userRepository, never())
                .findByOrganizationIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("an unrecognised ?role= is a 400, not an empty list")
    void listUsers_InvalidRoleFilter_Throws400() {
        // An empty list would read as "this organization has no such users", which is a different
        // and wrong answer to a typo.
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.listUsers(SUPER_ADMIN, null, "STUDNET"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("STUDNET"));
    }

    // -------------------------------------------------------------------------------------------
    // getUserById
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN reads any user, including one in an organization they do not belong to")
    void getUserById_SuperAdmin_ReturnsAnyUser() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 9L, false)));

        UserSummaryResponse result = adminUserService.getUserById(SUPER_ADMIN, null, 5L);

        assertEquals(5L, result.getId());
        // The password hash must never reach a response DTO.
        assertEquals("STUDENT", result.getRole());
    }

    @Test
    @DisplayName("ORG_ADMIN reads a user in their own organization")
    void getUserById_OrgAdmin_OwnOrg_Returns200() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));

        UserSummaryResponse result = adminUserService.getUserById(ORG_ADMIN, 7L, 5L);

        assertEquals(5L, result.getId());
    }

    @Test
    @DisplayName("ORG_ADMIN is refused a user from another organization")
    void getUserById_OrgAdmin_OtherOrg_Throws403() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 9L, false)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.getUserById(ORG_ADMIN, 7L, 5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("a missing user is a 404")
    void getUserById_NotFound_Throws404() {
        when(userRepository.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.getUserById(SUPER_ADMIN, null, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // changeUserRole
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN changes a user's role")
    void changeUserRole_SuperAdmin_ChangesRole() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse result = adminUserService.changeUserRole(SUPER_ADMIN, CALLER_ID, 5L, "MENTOR");

        assertEquals("MENTOR", result.getRole());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.MENTOR, saved.getValue().getRole());
    }

    @Test
    @DisplayName("ORG_ADMIN cannot change roles, and the user is never loaded")
    void changeUserRole_OrgAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.changeUserRole(ORG_ADMIN, CALLER_ID, 5L, "MENTOR"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // 403 before the lookup: an ORG_ADMIN probing ids must not learn which users exist.
        verify(userRepository, never()).findByIdAndIsDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("an unrecognised role is a 400 naming the offending value, not a 500")
    void changeUserRole_InvalidRole_Throws400() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.changeUserRole(SUPER_ADMIN, CALLER_ID, 5L, "WIZARD"));

        // Role.valueOf throws IllegalArgumentException, which the catch-all handler would report as
        // a 500. This is what converting it in parseRole buys.
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("WIZARD"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a SUPER_ADMIN cannot change their own role")
    void changeUserRole_Self_Throws400() {
        // Self-lockout guard: the last SUPER_ADMIN demoting themselves leaves the platform with no
        // administrator and no API route back.
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.changeUserRole(SUPER_ADMIN, CALLER_ID, CALLER_ID, "STUDENT"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).findByIdAndIsDeletedFalse(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("demoting the last active SUPER_ADMIN is refused")
    void changeUserRole_DemoteLastSuperAdmin_Throws400() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.SUPER_ADMIN, null, false)));
        when(userRepository.countByRoleAndIsDeletedFalse(Role.SUPER_ADMIN)).thenReturn(1L);

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.changeUserRole(SUPER_ADMIN, CALLER_ID, 5L, "STUDENT"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("SUPER_ADMIN must remain"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("promoting someone TO SUPER_ADMIN never runs the last-admin check")
    void changeUserRole_PromoteToSuperAdmin_SkipsGuard() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        adminUserService.changeUserRole(SUPER_ADMIN, CALLER_ID, 5L, "SUPER_ADMIN");

        // Only a demotion out of SUPER_ADMIN can reduce the count, so the query is skipped entirely.
        verify(userRepository, never()).countByRoleAndIsDeletedFalse(any(Role.class));
    }

    // -------------------------------------------------------------------------------------------
    // linkOrganization
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN links a user to an organization")
    void linkOrganization_SuperAdmin_SetsOrganizationId() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.PLACEMENT_OFFICER, null, false)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse result = adminUserService.linkOrganization(SUPER_ADMIN, 5L, 7L);

        assertEquals(7L, result.getOrganizationId());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(7L, saved.getValue().getOrganizationId());
    }

    @Test
    @DisplayName("SUPER_ADMIN can unlink a user by passing a null organizationId")
    void linkOrganization_NullOrganizationId_Unlinks() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.PLACEMENT_OFFICER, 7L, false)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse result = adminUserService.linkOrganization(SUPER_ADMIN, 5L, null);

        assertEquals(null, result.getOrganizationId());
    }

    @Test
    @DisplayName("ORG_ADMIN cannot link a user to an organization, and the user is never loaded")
    void linkOrganization_OrgAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.linkOrganization(ORG_ADMIN, 5L, 7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("linking a non-existent user throws 404")
    void linkOrganization_UnknownUser_Throws404() {
        when(userRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.linkOrganization(SUPER_ADMIN, 999L, 7L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // deactivateUser / activateUser
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deactivate sets isDeleted and never hard-deletes the row")
    void deactivateUser_SuperAdmin_SetsIsDeleted() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse result = adminUserService.deactivateUser(SUPER_ADMIN, CALLER_ID, null, 5L);

        assertTrue(result.getIsDeleted());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().getIsDeleted());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("activate clears isDeleted, loading through findById because the user IS deleted")
    void activateUser_SuperAdmin_ClearsIsDeleted() {
        // findByIdAndIsDeletedFalse would return empty here and turn every legitimate reactivation
        // into a 404 -- which is exactly why activateUser uses the plain finder.
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, true)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse result = adminUserService.activateUser(SUPER_ADMIN, null, 5L);

        assertFalse(result.getIsDeleted());
        verify(userRepository, never()).findByIdAndIsDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("deactivating an already-deactivated user is a 400")
    void deactivateUser_AlreadyDeactivated_Throws400() {
        // Stubbed to return a deleted user so the guard is actually reachable; in production
        // findByIdAndIsDeletedFalse 404s first. The check stays because it is the contract the
        // endpoint promises and a switch to a plain findById would otherwise silently lose it.
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, true)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.deactivateUser(SUPER_ADMIN, CALLER_ID, null, 5L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("activating an already-active user is a 400")
    void activateUser_AlreadyActive_Throws400() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.STUDENT, 7L, false)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.activateUser(SUPER_ADMIN, null, 5L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an admin cannot deactivate their own account")
    void deactivateUser_Self_Throws400() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.deactivateUser(SUPER_ADMIN, CALLER_ID, null, CALLER_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivating the last active SUPER_ADMIN is refused")
    void deactivateUser_LastSuperAdmin_Throws400() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.SUPER_ADMIN, null, false)));
        when(userRepository.countByRoleAndIsDeletedFalse(Role.SUPER_ADMIN)).thenReturn(1L);

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.deactivateUser(SUPER_ADMIN, CALLER_ID, null, 5L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("SUPER_ADMIN must remain"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ORG_ADMIN cannot deactivate a user from another organization")
    void deactivateUser_OrgAdmin_OtherOrg_Throws403() {
        when(userRepository.findByIdAndIsDeletedFalse(5L))
                .thenReturn(Optional.of(user(5L, Role.STUDENT, 9L, false)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.deactivateUser(ORG_ADMIN, CALLER_ID, 7L, 5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // getPlatformStats
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN stats use the global counters")
    void getPlatformStats_SuperAdmin_ReturnsGlobalCounts() {
        when(userRepository.countByIsDeletedFalse()).thenReturn(10L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.STUDENT)).thenReturn(6L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.ORG_ADMIN)).thenReturn(2L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.RECRUITER)).thenReturn(1L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.MENTOR)).thenReturn(0L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.PLACEMENT_OFFICER)).thenReturn(0L);
        when(userRepository.countByRoleAndIsDeletedFalse(Role.SUPER_ADMIN)).thenReturn(1L);

        AdminStatsResponse stats = adminUserService.getPlatformStats(SUPER_ADMIN, null);

        assertEquals(10L, stats.getTotalUsers());
        assertEquals(6L, stats.getTotalStudents());
        assertEquals(10L, stats.getActiveUsers());
        // The six role counts must account for every user, or a role is missing a field.
        long sum = stats.getTotalStudents() + stats.getTotalOrgAdmins() + stats.getTotalRecruiters()
                + stats.getTotalMentors() + stats.getTotalPlacementOfficers() + stats.getTotalSuperAdmins();
        assertEquals(stats.getTotalUsers(), sum);
        verify(userRepository, never()).countByOrganizationIdAndIsDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("ORG_ADMIN stats are org-scoped and never touch the global counters")
    void getPlatformStats_OrgAdmin_ReturnsScopedCounts() {
        when(userRepository.countByOrganizationIdAndIsDeletedFalse(7L)).thenReturn(4L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.STUDENT)).thenReturn(3L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.ORG_ADMIN)).thenReturn(1L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.RECRUITER)).thenReturn(0L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.MENTOR)).thenReturn(0L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.PLACEMENT_OFFICER))
                .thenReturn(0L);
        when(userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(7L, Role.SUPER_ADMIN)).thenReturn(0L);

        AdminStatsResponse stats = adminUserService.getPlatformStats(ORG_ADMIN, 7L);

        assertEquals(4L, stats.getTotalUsers());
        assertEquals(3L, stats.getTotalStudents());
        // Using the global countByRole* here would leak the whole platform's composition to one
        // college's admin.
        verify(userRepository, never()).countByRoleAndIsDeletedFalse(any(Role.class));
        verify(userRepository, never()).countByIsDeletedFalse();
    }

    @Test
    @DisplayName("a STUDENT cannot read platform stats")
    void getPlatformStats_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminUserService.getPlatformStats(STUDENT, 7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).countByIsDeletedFalse();
    }
}

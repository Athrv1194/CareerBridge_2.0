package com.careerbridge.auth;

import com.careerbridge.auth.dto.JoinRequestResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.JoinRequestStatus;
import com.careerbridge.auth.model.OrganizationJoinRequest;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.OrganizationJoinRequestRepository;
import com.careerbridge.auth.repository.UserRepository;
import com.careerbridge.auth.service.OrganizationJoinRequestServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure Mockito, matching AdminUserServiceTest's shape -- no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class OrganizationJoinRequestServiceTest {

    private static final String ORG_ADMIN = "ORG_ADMIN";
    private static final String STUDENT = "STUDENT";
    private static final Long CALLER_ID = 5L;
    private static final Long REVIEWER_ID = 100L;

    @Mock
    private OrganizationJoinRequestRepository joinRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationJoinRequestServiceImpl service;

    private static User user(Long id, Role role, Long orgId) {
        return User.builder().id(id).firstName("Test").lastName("User")
                .email("user" + id + "@careerbridge.test").password("hashed").role(role)
                .organizationId(orgId).isDeleted(false).build();
    }

    private static OrganizationJoinRequest request(Long id, Long userId, Long orgId, JoinRequestStatus status) {
        return OrganizationJoinRequest.builder().id(id).userId(userId).organizationId(orgId).status(status).build();
    }

    // -------------------------------------------------------------------------------------------
    // submit
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unlinked STUDENT can submit a join request")
    void submit_UnlinkedStudent_CreatesPendingRequest() {
        when(userRepository.findByIdAndIsDeletedFalse(CALLER_ID))
                .thenReturn(Optional.of(user(CALLER_ID, Role.STUDENT, null)));
        when(joinRequestRepository.existsByUserIdAndStatus(CALLER_ID, JoinRequestStatus.PENDING)).thenReturn(false);
        when(joinRequestRepository.save(any(OrganizationJoinRequest.class))).thenAnswer(i -> {
            OrganizationJoinRequest r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        JoinRequestResponse result = service.submit(CALLER_ID, STUDENT, 7L);

        assertEquals("PENDING", result.getStatus());
        assertEquals(7L, result.getOrganizationId());
        ArgumentCaptor<OrganizationJoinRequest> saved = ArgumentCaptor.forClass(OrganizationJoinRequest.class);
        verify(joinRequestRepository).save(saved.capture());
        assertEquals(CALLER_ID, saved.getValue().getUserId());
    }

    @Test
    @DisplayName("a RECRUITER cannot submit a join request -- recruiters belong to no organization")
    void submit_Recruiter_Throws403() {
        when(userRepository.findByIdAndIsDeletedFalse(CALLER_ID))
                .thenReturn(Optional.of(user(CALLER_ID, Role.RECRUITER, null)));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.submit(CALLER_ID, "RECRUITER", 7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("a user already linked to an organization cannot submit a join request")
    void submit_AlreadyLinked_Throws400() {
        when(userRepository.findByIdAndIsDeletedFalse(CALLER_ID))
                .thenReturn(Optional.of(user(CALLER_ID, Role.STUDENT, 3L)));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.submit(CALLER_ID, STUDENT, 7L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second pending request from the same user is refused")
    void submit_AlreadyPending_Throws409() {
        when(userRepository.findByIdAndIsDeletedFalse(CALLER_ID))
                .thenReturn(Optional.of(user(CALLER_ID, Role.STUDENT, null)));
        when(joinRequestRepository.existsByUserIdAndStatus(CALLER_ID, JoinRequestStatus.PENDING)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.submit(CALLER_ID, STUDENT, 7L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(joinRequestRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // listForOrg
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ORG_ADMIN with no X-User-Org-Id sees an empty list, never every request")
    void listForOrg_NoOrgId_ReturnsEmpty() {
        List<JoinRequestResponse> result = service.listForOrg(ORG_ADMIN, null, null);

        assertEquals(0, result.size());
        verify(joinRequestRepository, never()).findByOrganizationIdOrderByRequestedAtDesc(any());
    }

    @Test
    @DisplayName("a STUDENT cannot list join requests")
    void listForOrg_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> service.listForOrg(STUDENT, 7L, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // approve
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ORG_ADMIN approving a pending request sets the user's organizationId")
    void approve_Pending_SetsUserOrganizationId() {
        when(joinRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(1L, CALLER_ID, 7L, JoinRequestStatus.PENDING)));
        when(userRepository.findByIdAndIsDeletedFalse(CALLER_ID))
                .thenReturn(Optional.of(user(CALLER_ID, Role.STUDENT, null)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(joinRequestRepository.save(any(OrganizationJoinRequest.class))).thenAnswer(i -> i.getArgument(0));

        JoinRequestResponse result = service.approve(1L, ORG_ADMIN, 7L, REVIEWER_ID);

        assertEquals("APPROVED", result.getStatus());
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(7L, savedUser.getValue().getOrganizationId());
    }

    @Test
    @DisplayName("an ORG_ADMIN cannot approve a request for another organization")
    void approve_WrongOrg_Throws403() {
        when(joinRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(1L, CALLER_ID, 7L, JoinRequestStatus.PENDING)));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.approve(1L, ORG_ADMIN, 9L, REVIEWER_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("approving an already-reviewed request is a 409, and the user is never touched again")
    void approve_AlreadyReviewed_Throws409() {
        when(joinRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(1L, CALLER_ID, 7L, JoinRequestStatus.APPROVED)));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.approve(1L, ORG_ADMIN, 7L, REVIEWER_ID));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    // -------------------------------------------------------------------------------------------
    // reject
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ORG_ADMIN rejecting a pending request never touches the user row")
    void reject_Pending_NeverTouchesUser() {
        when(joinRequestRepository.findById(1L))
                .thenReturn(Optional.of(request(1L, CALLER_ID, 7L, JoinRequestStatus.PENDING)));
        when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(user(CALLER_ID, Role.STUDENT, null)));
        when(joinRequestRepository.save(any(OrganizationJoinRequest.class))).thenAnswer(i -> i.getArgument(0));

        JoinRequestResponse result = service.reject(1L, ORG_ADMIN, 7L, REVIEWER_ID);

        assertEquals("REJECTED", result.getStatus());
        verify(userRepository, never()).save(any());
    }
}

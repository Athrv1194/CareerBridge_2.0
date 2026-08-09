package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.JoinRequestResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.JoinRequestStatus;
import com.careerbridge.auth.model.OrganizationJoinRequest;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.OrganizationJoinRequestRepository;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class OrganizationJoinRequestServiceImpl implements OrganizationJoinRequestService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationJoinRequestServiceImpl.class);

    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";
    private static final Set<Role> JOINABLE_ROLES = EnumSet.of(Role.STUDENT, Role.PLACEMENT_OFFICER, Role.MENTOR);

    private final OrganizationJoinRequestRepository joinRequestRepository;
    private final UserRepository userRepository;

    public OrganizationJoinRequestServiceImpl(OrganizationJoinRequestRepository joinRequestRepository,
                                              UserRepository userRepository) {
        this.joinRequestRepository = joinRequestRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public JoinRequestResponse submit(Long callerId, String callerRole, Long organizationId) {
        User user = userRepository.findByIdAndIsDeletedFalse(callerId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!JOINABLE_ROLES.contains(user.getRole())) {
            throw new CustomException(
                    "Only students, placement officers and mentors can request to join an organization",
                    HttpStatus.FORBIDDEN);
        }
        if (user.getOrganizationId() != null) {
            throw new CustomException("You already belong to an organization", HttpStatus.BAD_REQUEST);
        }
        if (joinRequestRepository.existsByUserIdAndStatus(callerId, JoinRequestStatus.PENDING)) {
            throw new CustomException("You already have a pending join request", HttpStatus.CONFLICT);
        }

        OrganizationJoinRequest saved = joinRequestRepository.save(OrganizationJoinRequest.builder()
                .userId(callerId)
                .organizationId(organizationId)
                .build());

        log.info("Join request submitted: userId={} organizationId={}", callerId, organizationId);

        return toResponse(saved, user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listForOrg(String callerRole, Long callerOrgId, String statusFilter) {
        requireOrgAdmin(callerRole);

        // Same rule as every other org-scoped listing in this project (AdminUserServiceImpl.listUsers,
        // prs-service's leaderboard): a caller with no tenant sees nothing, never everything.
        if (callerOrgId == null) {
            log.warn("ORG_ADMIN requested join requests with no X-User-Org-Id; returning empty");
            return List.of();
        }

        List<OrganizationJoinRequest> requests = (statusFilter == null || statusFilter.isBlank())
                ? joinRequestRepository.findByOrganizationIdOrderByRequestedAtDesc(callerOrgId)
                : joinRequestRepository.findByOrganizationIdAndStatusOrderByRequestedAtDesc(
                        callerOrgId, parseStatus(statusFilter));

        return requests.stream().map(r -> toResponse(r, userRepository.findById(r.getUserId()).orElse(null))).toList();
    }

    @Override
    @Transactional
    public JoinRequestResponse approve(Long requestId, String callerRole, Long callerOrgId, Long callerId) {
        requireOrgAdmin(callerRole);

        OrganizationJoinRequest request = findOrThrow(requestId);
        requireOwnOrg(callerOrgId, request);

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new CustomException("This request has already been reviewed", HttpStatus.CONFLICT);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(request.getUserId())
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        user.setOrganizationId(request.getOrganizationId());
        userRepository.save(user);

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedByUserId(callerId);
        OrganizationJoinRequest saved = joinRequestRepository.save(request);

        log.info("Join request approved: requestId={} userId={} organizationId={} by callerId={}",
                requestId, request.getUserId(), request.getOrganizationId(), callerId);

        return toResponse(saved, user);
    }

    @Override
    @Transactional
    public JoinRequestResponse reject(Long requestId, String callerRole, Long callerOrgId, Long callerId) {
        requireOrgAdmin(callerRole);

        OrganizationJoinRequest request = findOrThrow(requestId);
        requireOwnOrg(callerOrgId, request);

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new CustomException("This request has already been reviewed", HttpStatus.CONFLICT);
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedByUserId(callerId);
        OrganizationJoinRequest saved = joinRequestRepository.save(request);

        log.info("Join request rejected: requestId={} userId={} by callerId={}",
                requestId, request.getUserId(), callerId);

        return toResponse(saved, userRepository.findById(request.getUserId()).orElse(null));
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization and lookups
    // ---------------------------------------------------------------------------------------------

    private void requireOrgAdmin(String callerRole) {
        if (!ROLE_ORG_ADMIN.equals(callerRole)) {
            throw new CustomException("Only ORG_ADMIN may perform this operation", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Objects.equals, never ==: both sides are boxed Long outside the Integer cache, same rule as
     * every other org-ownership check in this project (organization-service's
     * requireCanAccessOrganization, recruiter-service's job-ownership checks).
     */
    private void requireOwnOrg(Long callerOrgId, OrganizationJoinRequest request) {
        if (!Objects.equals(callerOrgId, request.getOrganizationId())) {
            throw new CustomException("You do not have access to this request", HttpStatus.FORBIDDEN);
        }
    }

    private OrganizationJoinRequest findOrThrow(Long id) {
        return joinRequestRepository.findById(id)
                .orElseThrow(() -> new CustomException("Join request not found", HttpStatus.NOT_FOUND));
    }

    private JoinRequestStatus parseStatus(String raw) {
        try {
            return JoinRequestStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CustomException("Invalid status: '" + raw + "'", HttpStatus.BAD_REQUEST);
        }
    }

    private JoinRequestResponse toResponse(OrganizationJoinRequest r, User user) {
        return JoinRequestResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .email(user != null ? user.getEmail() : null)
                .role(user != null ? user.getRole().name() : null)
                .organizationId(r.getOrganizationId())
                .status(r.getStatus().name())
                .requestedAt(r.getRequestedAt())
                .reviewedAt(r.getReviewedAt())
                .reviewedByUserId(r.getReviewedByUserId())
                .build();
    }
}

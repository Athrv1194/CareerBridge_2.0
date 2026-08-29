package com.careerbridge.auth.service;

import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.UserSummaryResponse;
import com.careerbridge.auth.event.UserDepartmentUpdatedEvent;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";

    private static final String USER_NOT_FOUND = "User not found";
    // Plain ASCII hyphen, deliberately: this is a wire-facing string and an em-dash would be the
    // only non-ASCII character in any API response in the codebase.
    private static final String LAST_SUPER_ADMIN =
            "Cannot perform this action - at least one active SUPER_ADMIN must remain.";

    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    public AdminUserServiceImpl(UserRepository userRepository, RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ---------------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers(String callerRole, Long callerOrgId, String roleFilter) {
        requireAdmin(callerRole);

        // Parsed before any query so a bad filter is a 400 naming the value, never an empty list a
        // caller would read as "this organization has no recruiters".
        Role filter = (roleFilter == null || roleFilter.isBlank()) ? null : parseRole(roleFilter);

        boolean superAdmin = ROLE_SUPER_ADMIN.equals(callerRole);

        // An ORG_ADMIN whose token carries no organization. Should not occur -- auth-service always
        // sets organizationId for that role -- but the only safe reading of a missing tenant is "no
        // tenant", never "every tenant". Same rule as prs-service's leaderboard.
        if (!superAdmin && callerOrgId == null) {
            log.warn("ORG_ADMIN requested the user list with no X-User-Org-Id; returning empty");
            return List.of();
        }

        List<User> users;
        if (superAdmin && filter == null) {
            users = userRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
        } else if (superAdmin) {
            users = userRepository.findByRoleAndIsDeletedFalse(filter);
        } else if (filter == null) {
            users = userRepository.findByOrganizationIdAndIsDeletedFalseOrderByCreatedAtDesc(callerOrgId);
        } else {
            users = userRepository.findByOrganizationIdAndRoleAndIsDeletedFalse(callerOrgId, filter);
        }

        return users.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getUserById(String callerRole, Long callerOrgId, Long targetUserId) {
        requireAdmin(callerRole);

        User user = userRepository.findByIdAndIsDeletedFalse(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        requireSameOrgIfOrgAdmin(callerRole, callerOrgId, user);

        return toResponse(user);
    }

    /** Self-service: no requireAdmin -- a caller is always entitled to read their own record. */
    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getOwnProfile(Long callerId) {
        User user = userRepository.findByIdAndIsDeletedFalse(callerId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getPlatformStats(String callerRole, Long callerOrgId) {
        requireAdmin(callerRole);

        if (ROLE_SUPER_ADMIN.equals(callerRole)) {
            return AdminStatsResponse.builder()
                    .totalUsers(userRepository.countByIsDeletedFalse())
                    .totalStudents(userRepository.countByRoleAndIsDeletedFalse(Role.STUDENT))
                    .totalOrgAdmins(userRepository.countByRoleAndIsDeletedFalse(Role.ORG_ADMIN))
                    .totalRecruiters(userRepository.countByRoleAndIsDeletedFalse(Role.RECRUITER))
                    .totalMentors(userRepository.countByRoleAndIsDeletedFalse(Role.MENTOR))
                    .totalPlacementOfficers(
                            userRepository.countByRoleAndIsDeletedFalse(Role.PLACEMENT_OFFICER))
                    .totalSuperAdmins(userRepository.countByRoleAndIsDeletedFalse(Role.SUPER_ADMIN))
                    .activeUsers(userRepository.countByIsDeletedFalse())
                    .build();
        }

        // ORG_ADMIN. Every count is org-scoped -- using the global countByRole* here would leak the
        // platform's composition to a single college's admin.
        if (callerOrgId == null) {
            log.warn("ORG_ADMIN requested stats with no X-User-Org-Id; returning zeroes");
            return zeroStats();
        }

        long orgTotal = userRepository.countByOrganizationIdAndIsDeletedFalse(callerOrgId);
        return AdminStatsResponse.builder()
                .totalUsers(orgTotal)
                .totalStudents(countInOrg(callerOrgId, Role.STUDENT))
                .totalOrgAdmins(countInOrg(callerOrgId, Role.ORG_ADMIN))
                .totalRecruiters(countInOrg(callerOrgId, Role.RECRUITER))
                .totalMentors(countInOrg(callerOrgId, Role.MENTOR))
                .totalPlacementOfficers(countInOrg(callerOrgId, Role.PLACEMENT_OFFICER))
                .totalSuperAdmins(countInOrg(callerOrgId, Role.SUPER_ADMIN))
                .activeUsers(orgTotal)
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------------

    /**
     * SUPER_ADMIN only. An ORG_ADMIN able to grant roles could grant themselves SUPER_ADMIN, which
     * would make every other check in this class decorative.
     */
    @Override
    @Transactional
    public UserSummaryResponse changeUserRole(String callerRole, Long callerId, Long targetUserId,
                                              String newRole) {
        requireSuperAdmin(callerRole);
        requireNotSelf(callerId, targetUserId, "change your own role");

        User user = userRepository.findByIdAndIsDeletedFalse(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Role parsed = parseRole(newRole);
        Role previous = user.getRole();

        // Only a demotion OUT of SUPER_ADMIN can reduce the count. Promoting someone TO SUPER_ADMIN,
        // or re-setting an existing SUPER_ADMIN to SUPER_ADMIN, never can.
        if (previous == Role.SUPER_ADMIN && parsed != Role.SUPER_ADMIN) {
            requireAnotherSuperAdminRemains();
        }

        user.setRole(parsed);
        User saved = userRepository.save(user);

        // A role change is the most privileged operation here; log it so there is a trail.
        log.info("Role changed for userId={} from {} to {}", targetUserId, previous, parsed);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserSummaryResponse deactivateUser(String callerRole, Long callerId, Long callerOrgId,
                                              Long targetUserId) {
        requireAdmin(callerRole);
        requireNotSelf(callerId, targetUserId, "deactivate your own account");

        User user = userRepository.findByIdAndIsDeletedFalse(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        requireSameOrgIfOrgAdmin(callerRole, callerOrgId, user);

        // findByIdAndIsDeletedFalse cannot return an already-deactivated user, so this is defensive
        // rather than reachable -- kept because it is the contract the endpoint promises, and a
        // future switch to a plain findById would otherwise silently lose the 400.
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new CustomException("User is already deactivated", HttpStatus.BAD_REQUEST);
        }

        // Deactivating any non-SUPER_ADMIN cannot affect the count, so the query is skipped.
        if (user.getRole() == Role.SUPER_ADMIN) {
            requireAnotherSuperAdminRemains();
        }

        user.setIsDeleted(true);
        User saved = userRepository.save(user);

        log.info("Deactivated userId={} by {}", targetUserId, callerRole);

        return toResponse(saved);
    }

    /**
     * Loads with the plain findById, NOT findByIdAndIsDeletedFalse. The user being reactivated is by
     * definition soft-deleted, so the filtered finder would return empty and turn every legitimate
     * reactivation into a 404.
     */
    @Override
    @Transactional
    public UserSummaryResponse linkOrganization(String callerRole, Long targetUserId, Long organizationId) {
        requireSuperAdmin(callerRole);

        User user = userRepository.findByIdAndIsDeletedFalse(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Long previous = user.getOrganizationId();
        user.setOrganizationId(organizationId);
        User saved = userRepository.save(user);

        log.info("organizationId changed for userId={} from {} to {}", targetUserId, previous, organizationId);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserSummaryResponse activateUser(String callerRole, Long callerOrgId, Long targetUserId) {
        requireAdmin(callerRole);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        requireSameOrgIfOrgAdmin(callerRole, callerOrgId, user);

        if (!Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new CustomException("User is already active", HttpStatus.BAD_REQUEST);
        }

        user.setIsDeleted(false);
        User saved = userRepository.save(user);

        log.info("Reactivated userId={} by {}", targetUserId, callerRole);

        return toResponse(saved);
    }

    /**
     * ORG_ADMIN or SUPER_ADMIN, scoped by requireSameOrgIfOrgAdmin exactly like deactivateUser --
     * an ORG_ADMIN may organise their own college's people and no one else's.
     *
     * Blank normalises to null rather than being stored: "" and "   " would otherwise each become a
     * distinct department key, grouping separately from genuinely unassigned users on any dashboard
     * that groups by this field.
     */
    @Override
    @Transactional
    public UserSummaryResponse assignDepartment(String callerRole, Long callerOrgId,
                                                Long targetUserId, String department) {
        requireAdmin(callerRole);

        User user = userRepository.findByIdAndIsDeletedFalse(targetUserId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        requireSameOrgIfOrgAdmin(callerRole, callerOrgId, user);

        return applyDepartment(user, department, "admin " + callerRole);
    }

    /**
     * Self-service counterpart to assignDepartment. No requireAdmin/requireSameOrgIfOrgAdmin -- a
     * caller acting on their OWN record needs no authorization beyond being that record's owner,
     * same reasoning as OrganizationJoinRequestService.submit needing no org check for the caller.
     */
    @Override
    @Transactional
    public UserSummaryResponse assignOwnDepartment(Long callerId, String department) {
        User user = userRepository.findByIdAndIsDeletedFalse(callerId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        return applyDepartment(user, department, "self");
    }

    /**
     * Shared by assignDepartment and assignOwnDepartment: the organizationId guard, blank-to-null
     * normalisation and save are identical either way -- only who is allowed to call it differs.
     *
     * A department is a subdivision of an organization, so it cannot be set on someone who has none
     * -- storing one would produce a user filed under a department no organization owns.
     *
     * Blank normalises to null rather than being stored: "" and "   " would otherwise each become a
     * distinct department key, grouping separately from genuinely unassigned users on any dashboard
     * that groups by this field.
     */
    private UserSummaryResponse applyDepartment(User user, String department, String actor) {
        if (user.getOrganizationId() == null) {
            throw new CustomException("User does not belong to an organization",
                    HttpStatus.BAD_REQUEST);
        }

        String normalised = (department == null || department.isBlank()) ? null : department.trim();

        String previous = user.getDepartment();
        user.setDepartment(normalised);
        User saved = userRepository.save(user);

        log.info("Department changed for userId={} from {} to {} (by {})",
                user.getId(), previous, normalised, actor);

        publishDepartmentUpdated(saved);

        return toResponse(saved);
    }

    /**
     * Tells student-service to update its local copy, which is what puts department on the public
     * candidate profile recruiter-service searches. An event rather than student-service reading
     * back from here -- see RabbitMQConfig.USER_DEPARTMENT_UPDATED_ROUTING_KEY for why a
     * synchronous read into auth-service is not possible.
     *
     * Fail-soft, matching every other publisher in this service: the row is already saved, so
     * rethrowing would report failure for work that actually happened. The cost of a lost event is
     * a stale department on the candidate profile until the next assignment, not a lost assignment.
     *
     * Published inside the surrounding @Transactional, so a rollback after this point would leave a
     * phantom event -- the same acknowledged trade-off as publishStudentRegistered, and acceptable
     * for the same reason: the consumer SETs an absolute value, so a phantom is corrected by the
     * next real event rather than compounding.
     */
    private void publishDepartmentUpdated(User user) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.USER_DEPARTMENT_UPDATED_ROUTING_KEY,
                    UserDepartmentUpdatedEvent.builder()
                            .userId(user.getId())
                            .department(user.getDepartment())
                            .organizationId(user.getOrganizationId())
                            .updatedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for userId={}: {}",
                    RabbitMQConfig.USER_DEPARTMENT_UPDATED_ROUTING_KEY, user.getId(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------------------------------

    private void requireAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole) && !ROLE_ORG_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN or ORG_ADMIN may perform this operation",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void requireSuperAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN may perform this operation",
                    HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Blocks self-lockout. The last SUPER_ADMIN demoting themselves to STUDENT, or deactivating
     * their own account, leaves the platform with no administrator and no way back through the API
     * -- recovery would need direct database access. Both operations are one call away, so the guard
     * is here rather than in a runbook.
     *
     * 400, not 403: the caller IS authorized, they are asking for something that makes no sense.
     *
     * Objects.equals, never ==: boxed Long on both sides, and a real userId is far outside the
     * Integer cache, so reference comparison would let exactly the case this blocks slip through.
     *
     * Deliberately not applied to activateUser -- a deactivated admin cannot log in (AuthServiceImpl
     * .requireActive), so they can never be the caller reactivating themselves.
     */
    private void requireNotSelf(Long callerId, Long targetUserId, String action) {
        if (Objects.equals(callerId, targetUserId)) {
            throw new CustomException("You cannot " + action, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Refuses an operation that would leave the platform with no active SUPER_ADMIN.
     *
     * Callers check first whether the target is actually a SUPER_ADMIN, so this query only runs on
     * the operations that could reduce the count.
     *
     * Mostly defence in depth: requireNotSelf already blocks the common path, since if only one
     * active SUPER_ADMIN exists then a SUPER_ADMIN caller IS that person and the self-guard fires
     * first. What this catches is the case the self-guard cannot -- a caller acting on a still-valid
     * JWT that outlived the row behind it (access tokens live ~15 minutes), where the header says
     * SUPER_ADMIN but the database no longer agrees. Cheap enough to be worth closing.
     *
     * <= 1 rather than == 1: if the count is somehow already zero, the answer is still "no".
     */
    private void requireAnotherSuperAdminRemains() {
        if (userRepository.countByRoleAndIsDeletedFalse(Role.SUPER_ADMIN) <= 1) {
            throw new CustomException(LAST_SUPER_ADMIN, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * A SUPER_ADMIN reaches any user; an ORG_ADMIN reaches only their own organization's.
     *
     * Objects.equals, never ==: both sides are Long objects well outside the Integer cache, so
     * reference comparison would be false for exactly the ids this exists to allow. A null on either
     * side never matches, which is right -- an ORG_ADMIN with no tenant, or a SUPER_ADMIN target who
     * belongs to none, must not be reachable by a tenant admin.
     */
    private void requireSameOrgIfOrgAdmin(String callerRole, Long callerOrgId, User user) {
        if (ROLE_SUPER_ADMIN.equals(callerRole)) {
            return;
        }
        if (!Objects.equals(user.getOrganizationId(), callerOrgId)) {
            throw new CustomException("You do not have access to this user", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Role.valueOf throws IllegalArgumentException, which the catch-all handler would report as a
     * 500. Converting it here gives the caller a 400 naming the value they sent -- which is the
     * whole reason ChangeRoleRequest.role is a String rather than the enum.
     */
    private Role parseRole(String newRole) {
        try {
            return Role.valueOf(newRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CustomException("Invalid role: '" + newRole + "'", HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------------------------

    private long countInOrg(Long organizationId, Role role) {
        return userRepository.countByOrganizationIdAndRoleAndIsDeletedFalse(organizationId, role);
    }

    private AdminStatsResponse zeroStats() {
        return AdminStatsResponse.builder()
                .totalUsers(0L).totalStudents(0L).totalOrgAdmins(0L).totalRecruiters(0L)
                .totalMentors(0L).totalPlacementOfficers(0L).totalSuperAdmins(0L).activeUsers(0L)
                .build();
    }

    /** Note what is absent: password. See UserSummaryResponse's class comment. */
    private UserSummaryResponse toResponse(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                // name(), not toString(): identical today, but toString() is overridable and this is
                // a wire contract.
                .role(user.getRole() == null ? null : user.getRole().name())
                .organizationId(user.getOrganizationId())
                .department(user.getDepartment())
                .subscriptionPlan(user.getSubscriptionPlan())
                .isDeleted(user.getIsDeleted())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

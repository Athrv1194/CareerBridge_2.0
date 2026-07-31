package com.careerbridge.auth.repository;

import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Boolean existsByEmail(String email);

    // ---------------------------------------------------------------------------------------------
    // Admin module. Every finder below filters isDeleted = false, EXCEPT the plain findById inherited
    // from JpaRepository, which activateUser uses deliberately -- a deactivated user is precisely the
    // one it needs to find.
    // ---------------------------------------------------------------------------------------------

    /** SUPER_ADMIN's user list: every active user on the platform, newest first. */
    List<User> findByIsDeletedFalseOrderByCreatedAtDesc();

    /** ORG_ADMIN's user list, scoped to their own tenant. Rows with a null organizationId never match. */
    List<User> findByOrganizationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organizationId);

    List<User> findByRoleAndIsDeletedFalse(Role role);

    List<User> findByOrganizationIdAndRoleAndIsDeletedFalse(Long organizationId, Role role);

    /**
     * An Optional is safe here: id is the primary key. Contrast with the List-returning finders in
     * roadmap-service and prs-service, where the queried columns are not unique.
     */
    Optional<User> findByIdAndIsDeletedFalse(Long id);

    long countByIsDeletedFalse();

    long countByRoleAndIsDeletedFalse(Role role);

    long countByOrganizationIdAndIsDeletedFalse(Long organizationId);

    /** ORG_ADMIN's per-role counts. Needed because the two-arg count above is org-wide, not per role. */
    long countByOrganizationIdAndRoleAndIsDeletedFalse(Long organizationId, Role role);
}

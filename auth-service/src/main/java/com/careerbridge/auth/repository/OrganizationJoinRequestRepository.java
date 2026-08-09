package com.careerbridge.auth.repository;

import com.careerbridge.auth.model.JoinRequestStatus;
import com.careerbridge.auth.model.OrganizationJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationJoinRequestRepository extends JpaRepository<OrganizationJoinRequest, Long> {

    // The TOCTOU-safe guarantee is a unique partial index the service layer cannot see from here;
    // this is the fast-path check, same role as CompanyServiceImpl.existsByRecruiterId in
    // recruiter-service -- it stops the common case, a real constraint stops the race.
    boolean existsByUserIdAndStatus(Long userId, JoinRequestStatus status);

    List<OrganizationJoinRequest> findByOrganizationIdAndStatusOrderByRequestedAtDesc(
            Long organizationId, JoinRequestStatus status);

    List<OrganizationJoinRequest> findByOrganizationIdOrderByRequestedAtDesc(Long organizationId);
}

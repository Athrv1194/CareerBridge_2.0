package com.careerbridge.organization.repository;

import com.careerbridge.organization.model.OrganizationRequest;
import com.careerbridge.organization.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationRequestRepository extends JpaRepository<OrganizationRequest, Long> {

    boolean existsByInstitutionCodeIgnoreCase(String institutionCode);

    /** Blocks a second application for the same college while an earlier one is still PENDING. */
    boolean existsByInstitutionNameIgnoreCaseAndStatus(String institutionName, RequestStatus status);

    List<OrganizationRequest> findByStatusOrderByRequestedAtDesc(RequestStatus status);

    List<OrganizationRequest> findAllByOrderByRequestedAtDesc();
}

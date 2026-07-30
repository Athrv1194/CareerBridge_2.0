package com.careerbridge.organization.service;

import com.careerbridge.organization.dto.CreateDepartmentRequest;
import com.careerbridge.organization.dto.CreateOrganizationRequest;
import com.careerbridge.organization.dto.DepartmentResponse;
import com.careerbridge.organization.dto.OrganizationResponse;
import com.careerbridge.organization.dto.UpdateOrganizationRequest;

import java.util.List;

/**
 * Every method takes the caller's role and, where tenant scoping applies, their organization id.
 * Authorization lives here rather than in the controller or the gateway: the gateway validates the
 * JWT and forwards identity, but knows nothing about which organization a row belongs to.
 *
 * callerOrgId is nullable throughout: a SUPER_ADMIN belongs to no organization.
 */
public interface OrganizationService {

    OrganizationResponse createOrganization(CreateOrganizationRequest request, String callerRole);

    List<OrganizationResponse> getAllOrganizations(String callerRole);

    OrganizationResponse getOrganizationById(Long id, String callerRole, Long callerOrgId);

    OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request,
                                            String callerRole, Long callerOrgId);

    void deactivateOrganization(Long id, String callerRole);

    DepartmentResponse createDepartment(Long organizationId, CreateDepartmentRequest request,
                                        String callerRole, Long callerOrgId);

    List<DepartmentResponse> getDepartmentsByOrg(Long organizationId, String callerRole, Long callerOrgId);
}

package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.JoinRequestResponse;

import java.util.List;

// Self-service org linking for users who registered without an organizationId.
// Not self-approving: org ids are sequential integers, self-approval lets anyone guess a college id.
public interface OrganizationJoinRequestService {

    // Allowed roles: STUDENT, PLACEMENT_OFFICER, MENTOR.
    // Refuses if caller already has an org, or already has a PENDING request.
    JoinRequestResponse submit(Long callerId, String callerRole, Long organizationId);

    // ORG_ADMIN only, scoped to their own org. statusFilter null = all statuses.
    List<JoinRequestResponse> listForOrg(String callerRole, Long callerOrgId, String statusFilter);

    // Sets User.organizationId on approval.
    JoinRequestResponse approve(Long requestId, String callerRole, Long callerOrgId, Long callerId);

    // Leaves User.organizationId untouched.
    JoinRequestResponse reject(Long requestId, String callerRole, Long callerOrgId, Long callerId);
}

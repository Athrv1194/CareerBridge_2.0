package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.JoinRequestResponse;

import java.util.List;

/**
 * Self-service organization linking: a STUDENT, PLACEMENT_OFFICER or MENTOR who registered without
 * an organizationId (or was signed up before their college's org existed) submits a request naming
 * the organization id shown on that college's College Dashboard; that organization's own ORG_ADMIN
 * approves or rejects it. Approval is the only thing, besides the SUPER_ADMIN link-organization
 * endpoint, that can ever set User.organizationId after registration.
 *
 * Deliberately NOT self-approving: organization ids are small sequential integers, so allowing a
 * caller to set their own organizationId with no review would let anyone claim membership in any
 * college by guessing a number 1-2 higher or lower than their own.
 */
public interface OrganizationJoinRequestService {

    /**
     * callerRole must be STUDENT, PLACEMENT_OFFICER or MENTOR -- RECRUITER and SUPER_ADMIN belong to
     * no organization by design, and ORG_ADMIN is provisioned by the organization-request-approval
     * flow, never by asking to join one after the fact.
     *
     * Refuses a caller who already has an organizationId (this is for linking the unlinked, not
     * transferring an existing membership) and refuses a second PENDING request from the same user.
     */
    JoinRequestResponse submit(Long callerId, String callerRole, Long organizationId);

    /** ORG_ADMIN only, scoped to their own organization. statusFilter null means every status. */
    List<JoinRequestResponse> listForOrg(String callerRole, Long callerOrgId, String statusFilter);

    /** ORG_ADMIN only; the request's organizationId must equal the caller's own. Sets the user's organizationId. */
    JoinRequestResponse approve(Long requestId, String callerRole, Long callerOrgId, Long callerId);

    /** Same authorization as approve; leaves the user's organizationId untouched. */
    JoinRequestResponse reject(Long requestId, String callerRole, Long callerOrgId, Long callerId);
}

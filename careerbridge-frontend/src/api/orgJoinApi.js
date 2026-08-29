import { authedFetch } from './httpClient';

// Self-service: STUDENT, PLACEMENT_OFFICER or MENTOR asking to be linked to an organization they
// didn't (or couldn't) specify at registration. Reviewed by that org's own ORG_ADMIN.
export function submitJoinRequest(organizationId) {
  return authedFetch('/auth/me/organization-requests', {
    method: 'POST',
    body: JSON.stringify({ organizationId }),
  });
}

// ORG_ADMIN review queue, scoped to their own organization by the gateway-injected X-User-Org-Id.
export function listOrgJoinRequests(status) {
  return authedFetch(`/auth/admin/organization-requests${status ? `?status=${status}` : ''}`);
}

export function approveOrgJoinRequest(id) {
  return authedFetch(`/auth/admin/organization-requests/${id}/approve`, { method: 'PATCH' });
}

export function rejectOrgJoinRequest(id) {
  return authedFetch(`/auth/admin/organization-requests/${id}/reject`, { method: 'PATCH' });
}

import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

async function authedFetch(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
      ...options.headers,
    },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Something went wrong. Please try again.');
  }
  return res.status === 204 ? null : res.json();
}

// ---- auth-service: platform users ----
export function getPlatformStats() {
  return authedFetch('/auth/admin/stats');
}

export function listUsers(role) {
  return authedFetch(`/auth/admin/users${role ? `?role=${role}` : ''}`);
}

export function getUserById(userId) {
  return authedFetch(`/auth/admin/users/${userId}`);
}

export function changeUserRole(userId, role) {
  return authedFetch(`/auth/admin/users/${userId}/role`, { method: 'PATCH', body: JSON.stringify({ role }) });
}

export function deactivateUser(userId) {
  return authedFetch(`/auth/admin/users/${userId}/deactivate`, { method: 'PATCH' });
}

export function activateUser(userId) {
  return authedFetch(`/auth/admin/users/${userId}/activate`, { method: 'PATCH' });
}

// organizationId may be null to unlink a user from their institution.
export function linkUserOrganization(userId, organizationId) {
  return authedFetch(`/auth/admin/users/${userId}/organization`, {
    method: 'PATCH',
    body: JSON.stringify({ organizationId }),
  });
}

// ---- organization-service: organisations + departments ----
export function listOrganizations() {
  return authedFetch('/organization');
}

export function getOrganization(id) {
  return authedFetch(`/organization/${id}`);
}

export function listDepartments(orgId) {
  return authedFetch(`/organization/${orgId}/departments`);
}

export function createOrganization(payload) {
  return authedFetch('/organization', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateOrganization(id, payload) {
  return authedFetch(`/organization/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deactivateOrganization(id) {
  return authedFetch(`/organization/${id}`, { method: 'DELETE' });
}

export function createDepartment(orgId, payload) {
  return authedFetch(`/organization/${orgId}/departments`, { method: 'POST', body: JSON.stringify(payload) });
}

// ---- organization-service: institution join requests ----
export function listOrgRequests(status) {
  return authedFetch(`/organization/requests${status ? `?status=${status}` : ''}`);
}

export function approveOrgRequest(id) {
  return authedFetch(`/organization/requests/${id}/approve`, { method: 'POST' });
}

export function rejectOrgRequest(id, reason) {
  return authedFetch(`/organization/requests/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) });
}

// ---- assessment-service: question bank admin ----
export function listCategories() {
  return authedFetch('/assessment/categories');
}

export function listAdminQuestions(categoryId) {
  return authedFetch(`/assessment/admin/questions${categoryId ? `?categoryId=${categoryId}` : ''}`);
}

export function addQuestion(payload) {
  return authedFetch('/assessment/admin/questions', { method: 'POST', body: JSON.stringify(payload) });
}

export function editQuestion(id, payload) {
  return authedFetch(`/assessment/admin/questions/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function activateQuestion(id) {
  return authedFetch(`/assessment/admin/questions/${id}/activate`, { method: 'PATCH' });
}

export function deactivateQuestion(id) {
  return authedFetch(`/assessment/admin/questions/${id}/deactivate`, { method: 'PATCH' });
}

// ---- prs-service: global leaderboard ----
export function getLeaderboard() {
  return authedFetch('/prs/leaderboard');
}

// ---- payment-service: subscriptions ----
export function listSubscriptions() {
  return authedFetch('/payment/admin/subscriptions');
}

// ---- recruiter-service: placement stats ----
export function getPlacementStats() {
  return authedFetch('/recruiter/stats/placement');
}

// ---- ai-coach-service: resource catalog refresh ----
export function refreshAiCoachResources() {
  return authedFetch('/ai-coach/resources/refresh', { method: 'POST' });
}

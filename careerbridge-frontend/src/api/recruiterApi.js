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

// Open to every role, but still needs a valid JWT to get past the gateway.
export function getJobs() {
  return authedFetch('/recruiter/jobs');
}

export function getJobDetail(jobId) {
  return authedFetch(`/recruiter/jobs/${jobId}`);
}

export function getMyApplications() {
  return authedFetch('/recruiter/applications/my');
}

export function applyToJob(jobId, coverLetter) {
  return authedFetch(`/recruiter/jobs/${jobId}/apply`, {
    method: 'POST',
    body: JSON.stringify({ coverLetter: coverLetter || undefined }),
  });
}

// Placement officer / org admin only -- scoped to the caller's own organization by the gateway-injected X-User-Org-Id.
export function getOrgApplications() {
  return authedFetch('/recruiter/applications/org');
}

export function getCandidates({ skills, minScore, maxScore } = {}) {
  const params = new URLSearchParams();
  if (skills) params.set('skills', skills);
  if (minScore !== null && minScore !== undefined && minScore !== '') params.set('minScore', minScore);
  if (maxScore !== null && maxScore !== undefined && maxScore !== '') params.set('maxScore', maxScore);
  const qs = params.toString();
  return authedFetch(`/recruiter/candidates${qs ? `?${qs}` : ''}`);
}

// --- Recruiter-only: companies, jobs, applications, offers, interviews, stats ---

export function createCompany(body) {
  return authedFetch('/recruiter/companies', { method: 'POST', body: JSON.stringify(body) });
}

export function getMyCompanies() {
  return authedFetch('/recruiter/companies/my');
}

export function updateCompany(id, body) {
  return authedFetch(`/recruiter/companies/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function createJob(body) {
  return authedFetch('/recruiter/jobs', { method: 'POST', body: JSON.stringify(body) });
}

export function getMyJobs() {
  return authedFetch('/recruiter/jobs/my');
}

export function updateJob(id, body) {
  return authedFetch(`/recruiter/jobs/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function deactivateJob(id) {
  return authedFetch(`/recruiter/jobs/${id}/deactivate`, { method: 'PATCH' });
}

export function deleteJob(id) {
  return authedFetch(`/recruiter/jobs/${id}`, { method: 'DELETE' });
}

export function getJobApplications(jobId) {
  return authedFetch(`/recruiter/jobs/${jobId}/applications`);
}

export function updateApplicationStatus(applicationId, status) {
  return authedFetch(`/recruiter/applications/${applicationId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function extendOffer(applicationId, offeredCtc) {
  return authedFetch(`/recruiter/applications/${applicationId}/offer`, {
    method: 'PATCH',
    body: JSON.stringify({ offeredCtc }),
  });
}

export function scheduleInterview(applicationId, body) {
  return authedFetch(`/recruiter/applications/${applicationId}/interview`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getMyInterviews() {
  return authedFetch('/recruiter/interviews/my');
}

export function updateInterview(interviewId, body) {
  return authedFetch(`/recruiter/interviews/${interviewId}`, { method: 'PATCH', body: JSON.stringify(body) });
}

export function getMyPlacementStats() {
  return authedFetch('/recruiter/stats/my');
}

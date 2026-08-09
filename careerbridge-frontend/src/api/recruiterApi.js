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

// Public job board -- open to every role, but api-gateway still requires a valid JWT since
// /api/recruiter/** is not in its public-paths list, so this is only reachable while signed in.
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

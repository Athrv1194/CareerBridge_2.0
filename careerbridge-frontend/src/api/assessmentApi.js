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

// section: APTITUDE, DOMAIN_KNOWLEDGE, or SOFT_SKILLS.
export function startAttempt(section) {
  return authedFetch('/assessment/attempt/start', { method: 'POST', body: JSON.stringify({ section }) });
}

export function submitAttempt(attemptId, answers) {
  return authedFetch('/assessment/attempt/submit', {
    method: 'POST',
    body: JSON.stringify({ attemptId, answers }),
  });
}

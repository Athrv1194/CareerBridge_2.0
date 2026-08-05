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

export function getMyProfile() {
  return authedFetch('/student/profile');
}

function sleep(ms) {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

// Registration returns a token immediately, but the profile row is created asynchronously by
// student-service's student.registered consumer over RabbitMQ -- fetching right after a fresh
// registration can beat that by a few hundred ms and get a genuine 404. Retried a few times with
// a short gap rather than treated as a hard failure; the profile is present well within this
// window in the normal case.
export async function getMyProfileWithRetry(attempts = 4, delayMs = 700) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      // eslint-disable-next-line no-await-in-loop
      return await getMyProfile();
    } catch (e) {
      if (i === attempts - 1) throw e;
      // eslint-disable-next-line no-await-in-loop
      await sleep(delayMs);
    }
  }
  return null;
}

export function updateMyProfile(payload) {
  return authedFetch('/student/profile', { method: 'PUT', body: JSON.stringify(payload) });
}

export function addEducation(payload) {
  return authedFetch('/student/profile/education', { method: 'POST', body: JSON.stringify(payload) });
}

export function addSkill(payload) {
  return authedFetch('/student/profile/skills', { method: 'POST', body: JSON.stringify(payload) });
}

// student-service itself reads no identity for this endpoint, but api-gateway still requires a
// valid JWT on any path that isn't in its own public-paths list -- this one isn't, so a bare
// unauthenticated fetch 401s at the gateway before ever reaching student-service.
export async function getSkillSuggestions() {
  try {
    return await authedFetch('/student/profile/skills/suggestions');
  } catch {
    return [];
  }
}

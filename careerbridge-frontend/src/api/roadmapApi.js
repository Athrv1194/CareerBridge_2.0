import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

function authHeaders() {
  return { Authorization: `Bearer ${getAccessToken()}` };
}

// Idempotent on the backend: calling this again for a career the student already built just
// returns the existing roadmap, so callers never need to check "do they have one yet" first.
export async function buildRoadmap(careerName) {
  const res = await fetch(`${API_BASE}/roadmap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ careerName }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not build the roadmap.');
  }
  return res.json();
}

// 404 is real and expected -- "no roadmap yet" -- not a failure. Note this returns the STUDENT'S
// SINGLE NEWEST roadmap, not "the one for career X": a student can hold one roadmap per career
// (see buildRoadmap), but there is currently no way to list or pick among them from here.
export async function getMyRoadmap() {
  const res = await fetch(`${API_BASE}/roadmap/my`, { headers: authHeaders() });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Could not load your roadmap.');
  return res.json();
}

export async function completeMilestone(milestoneId) {
  const res = await fetch(`${API_BASE}/roadmap/milestone/${milestoneId}/complete`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not mark that milestone complete.');
  }
  return res.json();
}

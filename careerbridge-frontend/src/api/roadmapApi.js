import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

function authHeaders() {
  return { Authorization: `Bearer ${getAccessToken()}` };
}

// Safe to call again -- backend just returns the existing roadmap if one's already built.
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

// 404 just means no roadmap yet. Returns the active one -- whichever was built or activated most
// recently -- not necessarily the newest by creation date.
export async function getMyRoadmap() {
  const res = await fetch(`${API_BASE}/roadmap/my`, { headers: authHeaders() });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Could not load your roadmap.');
  return res.json();
}

// Every roadmap the student has built, active one first. Backs the roadmap switcher.
export async function getMyRoadmaps() {
  const res = await fetch(`${API_BASE}/roadmap/my/all`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load your roadmaps.');
  return res.json();
}

// Makes this roadmap the one getMyRoadmap returns, without resetting its progress.
export async function activateRoadmap(roadmapId) {
  const res = await fetch(`${API_BASE}/roadmap/${roadmapId}/activate`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not switch roadmaps.');
  }
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

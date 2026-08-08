import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Idempotent on the backend: calling this again for a career the student already built just
// returns the existing roadmap, so callers never need to check "do they have one yet" first.
export async function buildRoadmap(careerName) {
  const res = await fetch(`${API_BASE}/roadmap`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify({ careerName }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not build the roadmap.');
  }
  return res.json();
}

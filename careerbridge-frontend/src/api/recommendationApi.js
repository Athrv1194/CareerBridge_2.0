import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// 404 here is a real, expected state -- "no recommendation yet" -- not a failure, since a
// recommendation only exists once an assessment has been completed. Callers use the null to
// mean exactly that, the same way student-service treats a 404 profile as "not created yet".
export async function getMyRecommendation() {
  const res = await fetch(`${API_BASE}/recommendation/my`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Could not check your recommendation status.');
  return res.json();
}

// Empty list, not 404, when the student has no history yet.
export async function getRecommendationHistory() {
  const res = await fetch(`${API_BASE}/recommendation/history`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) throw new Error('Could not load your recommendation history.');
  return res.json();
}

// No id field on CareerPathDto -- keyed by name, the only stable identifier the backend gives it.
export async function getCareerCatalog() {
  const res = await fetch(`${API_BASE}/recommendation/careers`);
  if (!res.ok) throw new Error('Could not load the career catalogue.');
  return res.json();
}

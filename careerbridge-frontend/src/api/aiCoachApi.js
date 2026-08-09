import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

function authHeader() {
  return { Authorization: `Bearer ${getAccessToken()}` };
}

// [] means "no roadmap yet"; a list where every entry has resources: [] means the roadmap exists
// but the catalog hasn't been refreshed for this career. Both are legitimate, non-error states, so
// this never throws for either -- only a genuine network/5xx failure returns [].
export async function getMyResources() {
  try {
    const res = await fetch(`${API_BASE}/ai-coach/resources`, { headers: authHeader() });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

// List-view shape only (no messages) -- matches ChatSessionSummary on the backend.
export async function getSessions() {
  const res = await fetch(`${API_BASE}/ai-coach/sessions`, { headers: authHeader() });
  if (!res.ok) return [];
  return res.json();
}

export async function getSession(id) {
  const res = await fetch(`${API_BASE}/ai-coach/sessions/${id}`, { headers: authHeader() });
  if (!res.ok) throw new Error('Could not load that session.');
  return res.json();
}

export async function createSession() {
  const res = await fetch(`${API_BASE}/ai-coach/sessions`, { method: 'POST', headers: authHeader() });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not start a new session.');
  }
  return res.json();
}

export async function deleteSession(id) {
  const res = await fetch(`${API_BASE}/ai-coach/sessions/${id}`, { method: 'DELETE', headers: authHeader() });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not delete that session.');
  }
}

export async function sendCoachMessage(id, content) {
  const res = await fetch(`${API_BASE}/ai-coach/sessions/${id}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ content }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || "We couldn't reach the coach. Your message was not sent. Try again.");
  }
  return res.json();
}

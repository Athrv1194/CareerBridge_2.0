import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

function authHeaders() {
  return { Authorization: `Bearer ${getAccessToken()}` };
}

// 404 just means the mentor hasn't set up a profile yet -- not an error.
export async function getMyMentorProfile() {
  const res = await fetch(`${API_BASE}/mentor/profile/my`, { headers: authHeaders() });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Could not load your mentor profile.');
  return res.json();
}

export async function createMentorProfile(body) {
  const res = await fetch(`${API_BASE}/mentor/profile`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    const err = new Error(payload.message || 'Could not create your profile.');
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export async function updateMentorProfile(body) {
  const res = await fetch(`${API_BASE}/mentor/profile`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    throw new Error(payload.message || 'Could not save your profile.');
  }
  return res.json();
}

// Empty list, not an error, when there are no sessions yet.
export async function getMySessionsAsMentor() {
  const res = await fetch(`${API_BASE}/mentor/sessions/my/mentor`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load your sessions.');
  return res.json();
}

export async function respondToSession(sessionId, body) {
  const res = await fetch(`${API_BASE}/mentor/sessions/${sessionId}/respond`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    throw new Error(payload.message || 'Could not respond to that request.');
  }
  return res.json();
}

export async function completeSession(sessionId) {
  const res = await fetch(`${API_BASE}/mentor/sessions/${sessionId}/complete`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    throw new Error(payload.message || 'Could not mark that session complete.');
  }
  return res.json();
}

export async function cancelSession(sessionId) {
  const res = await fetch(`${API_BASE}/mentor/sessions/${sessionId}/cancel`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    throw new Error(payload.message || 'Could not cancel that session.');
  }
  return res.json();
}

// careerPath wins if both are set -- mirrors the backend's own precedence.
export async function browseMentors({ careerPath, expertise } = {}) {
  const params = new URLSearchParams();
  if (careerPath) params.set('careerPath', careerPath);
  else if (expertise) params.set('expertise', expertise);
  const qs = params.toString();
  const res = await fetch(`${API_BASE}/mentor/browse${qs ? `?${qs}` : ''}`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load mentors.');
  return res.json();
}

export async function bookSession(body) {
  const res = await fetch(`${API_BASE}/mentor/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    const err = new Error(payload.message || 'Could not send that request.');
    err.status = res.status;
    throw err;
  }
  return res.json();
}

// Empty list, not an error, when the student has no sessions yet.
export async function getMySessionsAsStudent() {
  const res = await fetch(`${API_BASE}/mentor/sessions/my/student`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load your sessions.');
  return res.json();
}

export async function submitSessionReview(sessionId, body) {
  const res = await fetch(`${API_BASE}/mentor/sessions/${sessionId}/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    const err = new Error(payload.message || 'Could not submit your review.');
    err.status = res.status;
    throw err;
  }
  return res.json();
}

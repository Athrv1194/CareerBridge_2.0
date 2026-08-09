import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Metadata only (no PDF bytes) -- that's what GET /my returns by design on the backend.
export async function getMyResumes() {
  const res = await fetch(`${API_BASE}/resume/my`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) return [];
  return res.json();
}

export async function generateResume() {
  const res = await fetch(`${API_BASE}/resume/generate`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not generate a résumé.');
  }
  return res.json();
}

export async function deleteResume(id) {
  const res = await fetch(`${API_BASE}/resume/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not delete that résumé.');
  }
}

// The download route returns raw PDF bytes behind the same Bearer auth as everything else -- a
// plain <a href> or window.open() hits it with no Authorization header and gets a 401. Fetch the
// bytes ourselves and hand the browser a throwaway blob: URL to save instead.
export async function downloadResume(id, fileName) {
  const res = await fetch(`${API_BASE}/resume/download/${id}`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) throw new Error('Could not download that résumé.');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || 'resume.pdf';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

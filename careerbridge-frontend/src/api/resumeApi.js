import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Metadata only, no PDF bytes.
export async function getMyResumes() {
  const res = await fetch(`${API_BASE}/resume/my`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) return [];
  return res.json();
}

// options: summary, include* toggles, jobDescription -- all optional, defaults to "include everything".
export async function generateResume(options) {
  const res = await fetch(`${API_BASE}/resume/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getAccessToken()}` },
    body: options ? JSON.stringify(options) : undefined,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not generate a résumé.');
  }
  return res.json();
}

export async function setDefaultResume(id) {
  const res = await fetch(`${API_BASE}/resume/${id}/default`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not set that résumé as default.');
  }
  return res.json();
}

export async function getResume(id) {
  const res = await fetch(`${API_BASE}/resume/${id}`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) throw new Error('Could not load that résumé.');
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

// A plain <a href> can't send the Bearer header, so fetch the PDF ourselves as a blob URL.
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

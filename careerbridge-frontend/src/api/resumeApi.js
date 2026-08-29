import { getAccessToken } from '../utils/tokenUtils';
import { authedFetch } from './httpClient';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Metadata only, no PDF bytes.
export async function getMyResumes() {
  try {
    return await authedFetch('/resume/my');
  } catch {
    return [];
  }
}

// Recruiter/placement-officer/admin viewing a candidate's résumés, not the student themselves.
export async function getStudentResumes(studentId) {
  try {
    return await authedFetch(`/resume/student/${studentId}`);
  } catch {
    return [];
  }
}

// options: summary, include* toggles, jobDescription -- all optional, defaults to "include everything".
export function generateResume(options) {
  return authedFetch('/resume/generate', {
    method: 'POST',
    body: options ? JSON.stringify(options) : undefined,
    fallbackMessage: 'Could not generate a résumé.',
  });
}

export function setDefaultResume(id) {
  return authedFetch(`/resume/${id}/default`, { method: 'PATCH', fallbackMessage: 'Could not set that résumé as default.' });
}

export function getResume(id) {
  return authedFetch(`/resume/${id}`, { fallbackMessage: 'Could not load that résumé.' });
}

export function deleteResume(id) {
  return authedFetch(`/resume/${id}`, { method: 'DELETE', fallbackMessage: 'Could not delete that résumé.' });
}

// A plain <a href> can't send the Bearer header, so fetch the PDF ourselves as a blob URL.
// Raw fetch, not authedFetch: a blob response doesn't fit the JSON-only shared client.
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

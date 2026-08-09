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

export function updateEducation(id, payload) {
  return authedFetch(`/student/profile/education/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteEducation(id) {
  return authedFetch(`/student/profile/education/${id}`, { method: 'DELETE' });
}

export function addSkill(payload) {
  return authedFetch('/student/profile/skills', { method: 'POST', body: JSON.stringify(payload) });
}

export function deleteSkill(id) {
  return authedFetch(`/student/profile/skills/${id}`, { method: 'DELETE' });
}

export function addProject(payload) {
  return authedFetch('/student/profile/projects', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateProject(id, payload) {
  return authedFetch(`/student/profile/projects/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteProject(id) {
  return authedFetch(`/student/profile/projects/${id}`, { method: 'DELETE' });
}

export function addCertificate(payload) {
  return authedFetch('/student/profile/certificates', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateCertificate(id, payload) {
  return authedFetch(`/student/profile/certificates/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteCertificate(id) {
  return authedFetch(`/student/profile/certificates/${id}`, { method: 'DELETE' });
}

export function addExperience(payload) {
  return authedFetch('/student/profile/experience', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateExperience(id, payload) {
  return authedFetch(`/student/profile/experience/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteExperience(id) {
  return authedFetch(`/student/profile/experience/${id}`, { method: 'DELETE' });
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

async function uploadImage(path, file) {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken()}` },
    body: form,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not upload that image.');
  }
}

// Both the avatar and a project cover live behind the same Bearer auth as everything else -- a
// plain <img src> can't attach that header, so the bytes are fetched here and handed to the
// browser as a throwaway blob: URL, same approach as resumeApi.downloadResume.
async function fetchImageAsBlobUrl(path) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Could not load that image.');
  return URL.createObjectURL(await res.blob());
}

export function uploadAvatar(file) {
  return uploadImage('/student/profile/avatar', file);
}

export function getAvatarBlobUrl() {
  return fetchImageAsBlobUrl('/student/profile/avatar');
}

export function deleteAvatar() {
  return authedFetch('/student/profile/avatar', { method: 'DELETE' });
}

export function uploadProjectCover(projectId, file) {
  return uploadImage(`/student/profile/projects/${projectId}/cover`, file);
}

export function getProjectCoverBlobUrl(projectId) {
  return fetchImageAsBlobUrl(`/student/profile/projects/${projectId}/cover`);
}

export function deleteProjectCover(projectId) {
  return authedFetch(`/student/profile/projects/${projectId}/cover`, { method: 'DELETE' });
}

export function uploadCertificateFile(certificateId, file) {
  return uploadImage(`/student/profile/certificates/${certificateId}/file`, file);
}

export function getCertificateFileBlobUrl(certificateId) {
  return fetchImageAsBlobUrl(`/student/profile/certificates/${certificateId}/file`);
}

export function deleteCertificateFile(certificateId) {
  return authedFetch(`/student/profile/certificates/${certificateId}/file`, { method: 'DELETE' });
}

import { getAccessToken } from '../utils/tokenUtils';
import { authedFetch } from './httpClient';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

export function getMyProfile() {
  return authedFetch('/student/profile');
}

// Recruiter/placement-officer/admin viewing a candidate's full profile, not the student themselves.
export function getCandidateProfile(studentId) {
  return authedFetch(`/student/profile/${studentId}`);
}

function sleep(ms) {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

// Profile row is created async on registration, so retry a few times instead of failing on a 404.
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

// Needs auth just to pass the gateway, even though the endpoint itself doesn't check identity.
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

// <img src> can't send a Bearer header, so fetch the bytes ourselves and hand back a blob URL.
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

// Recruiter/placement-officer/admin viewing a candidate's photo, not the student themselves.
export function getCandidateAvatarBlobUrl(studentId) {
  return fetchImageAsBlobUrl(`/student/profile/${studentId}/avatar`);
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

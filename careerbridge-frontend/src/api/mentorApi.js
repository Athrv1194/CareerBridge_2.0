import { authedFetch } from './httpClient';

// 404 just means the mentor hasn't set up a profile yet -- not an error.
export async function getMyMentorProfile() {
  try {
    return await authedFetch('/mentor/profile/my', { fallbackMessage: 'Could not load your mentor profile.' });
  } catch (e) {
    if (e.status === 404) return null;
    throw e;
  }
}

export function createMentorProfile(body) {
  return authedFetch('/mentor/profile', {
    method: 'POST',
    body: JSON.stringify(body),
    fallbackMessage: 'Could not create your profile.',
  });
}

export function updateMentorProfile(body) {
  return authedFetch('/mentor/profile', {
    method: 'PUT',
    body: JSON.stringify(body),
    fallbackMessage: 'Could not save your profile.',
  });
}

// Empty list, not an error, when there are no sessions yet.
export function getMySessionsAsMentor() {
  return authedFetch('/mentor/sessions/my/mentor', { fallbackMessage: 'Could not load your sessions.' });
}

export function respondToSession(sessionId, body) {
  return authedFetch(`/mentor/sessions/${sessionId}/respond`, {
    method: 'PATCH',
    body: JSON.stringify(body),
    fallbackMessage: 'Could not respond to that request.',
  });
}

export function completeSession(sessionId) {
  return authedFetch(`/mentor/sessions/${sessionId}/complete`, {
    method: 'PATCH',
    fallbackMessage: 'Could not mark that session complete.',
  });
}

export function cancelSession(sessionId) {
  return authedFetch(`/mentor/sessions/${sessionId}/cancel`, {
    method: 'PATCH',
    fallbackMessage: 'Could not cancel that session.',
  });
}

// careerPath wins if both are set -- mirrors the backend's own precedence.
export function browseMentors({ careerPath, expertise } = {}) {
  const params = new URLSearchParams();
  if (careerPath) params.set('careerPath', careerPath);
  else if (expertise) params.set('expertise', expertise);
  const qs = params.toString();
  return authedFetch(`/mentor/browse${qs ? `?${qs}` : ''}`, { fallbackMessage: 'Could not load mentors.' });
}

export function bookSession(body) {
  return authedFetch('/mentor/sessions', {
    method: 'POST',
    body: JSON.stringify(body),
    fallbackMessage: 'Could not send that request.',
  });
}

// Empty list, not an error, when the student has no sessions yet.
export function getMySessionsAsStudent() {
  return authedFetch('/mentor/sessions/my/student', { fallbackMessage: 'Could not load your sessions.' });
}

export function submitSessionReview(sessionId, body) {
  return authedFetch(`/mentor/sessions/${sessionId}/review`, {
    method: 'POST',
    body: JSON.stringify(body),
    fallbackMessage: 'Could not submit your review.',
  });
}

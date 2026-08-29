import { authedFetch } from './httpClient';

// Empty resources per milestone just means the catalog isn't refreshed yet, not an error.
export async function getMyResources() {
  try {
    return await authedFetch('/ai-coach/resources');
  } catch {
    return [];
  }
}

// List view only, no messages.
export async function getSessions() {
  try {
    return await authedFetch('/ai-coach/sessions');
  } catch {
    return [];
  }
}

export function getSession(id) {
  return authedFetch(`/ai-coach/sessions/${id}`, { fallbackMessage: 'Could not load that session.' });
}

export function createSession() {
  return authedFetch('/ai-coach/sessions', { method: 'POST', fallbackMessage: 'Could not start a new session.' });
}

export function deleteSession(id) {
  return authedFetch(`/ai-coach/sessions/${id}`, { method: 'DELETE', fallbackMessage: 'Could not delete that session.' });
}

export function sendCoachMessage(id, content) {
  return authedFetch(`/ai-coach/sessions/${id}/messages`, {
    method: 'POST',
    body: JSON.stringify({ content }),
    fallbackMessage: "We couldn't reach the coach. Your message was not sent. Try again.",
  });
}

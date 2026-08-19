import { authedFetch } from './httpClient';

// Safe to call again -- backend just returns the existing roadmap if one's already built.
export function buildRoadmap(careerName) {
  return authedFetch('/roadmap', {
    method: 'POST',
    body: JSON.stringify({ careerName }),
    fallbackMessage: 'Could not build the roadmap.',
  });
}

// 404 just means no roadmap yet. Returns the active one -- whichever was built or activated most
// recently -- not necessarily the newest by creation date.
export async function getMyRoadmap() {
  try {
    return await authedFetch('/roadmap/my', { fallbackMessage: 'Could not load your roadmap.' });
  } catch (e) {
    if (e.status === 404) return null;
    throw e;
  }
}

// Every roadmap the student has built, active one first. Backs the roadmap switcher.
export function getMyRoadmaps() {
  return authedFetch('/roadmap/my/all', { fallbackMessage: 'Could not load your roadmaps.' });
}

// Makes this roadmap the one getMyRoadmap returns, without resetting its progress.
export function activateRoadmap(roadmapId) {
  return authedFetch(`/roadmap/${roadmapId}/activate`, {
    method: 'PATCH',
    fallbackMessage: 'Could not switch roadmaps.',
  });
}

export function completeMilestone(milestoneId) {
  return authedFetch(`/roadmap/milestone/${milestoneId}/complete`, {
    method: 'PATCH',
    fallbackMessage: 'Could not mark that milestone complete.',
  });
}

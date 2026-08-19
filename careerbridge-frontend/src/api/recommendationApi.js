import { authedFetch } from './httpClient';

// 404 just means no recommendation yet -- not a failure.
export async function getMyRecommendation() {
  try {
    return await authedFetch('/recommendation/my', { fallbackMessage: 'Could not check your recommendation status.' });
  } catch (e) {
    if (e.status === 404) return null;
    throw e;
  }
}

// Empty list, not 404, when the student has no history yet.
export function getRecommendationHistory() {
  return authedFetch('/recommendation/history', { fallbackMessage: 'Could not load your recommendation history.' });
}

// Careers are keyed by name -- no id field on CareerPathDto. Public path (see gateway.public-paths):
// authedFetch still attaches whatever token is in storage, but the gateway strips it for this
// route regardless, so an unauthenticated visitor and a logged-in one get the same request.
export function getCareerCatalog() {
  return authedFetch('/recommendation/careers', { fallbackMessage: 'Could not load the career catalogue.' });
}

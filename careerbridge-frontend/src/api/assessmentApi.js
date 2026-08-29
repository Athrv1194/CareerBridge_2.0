import { authedFetch } from './httpClient';

// section: APTITUDE, DOMAIN_KNOWLEDGE, or SOFT_SKILLS.
export function startAttempt(section) {
  return authedFetch('/assessment/attempt/start', { method: 'POST', body: JSON.stringify({ section }) });
}

export function submitAttempt(attemptId, answers) {
  return authedFetch('/assessment/attempt/submit', {
    method: 'POST',
    body: JSON.stringify({ attemptId, answers }),
  });
}

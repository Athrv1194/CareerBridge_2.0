const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

export async function submitInstitutionRequest(payload) {
  const res = await fetch(`${API_BASE}/organization/apply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      body.message
      || (res.status === 409
        ? 'An application for this institution already exists.'
        : 'Something went wrong submitting your application. Please try again.'),
    );
  }
  return body;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

export async function register({ firstName, lastName, email, password, role }) {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firstName, lastName, email, password, role }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(
      body.message || (res.status === 409 ? 'An account with this email already exists.' : 'Registration failed. Please try again.'),
    );
  }
  return res.json();
}

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

export async function login({ email, password }) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Check your email and password and try again.');
  }
  return res.json();
}

async function postAuthJson(path, payload, fallbackMessage) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.message || fallbackMessage);
  }
  return body;
}

export function forgotPassword(email) {
  return postAuthJson('/auth/forgot-password', { email }, 'Something went wrong. Please try again.');
}

export function verifyOtp({ email, otp }) {
  return postAuthJson('/auth/forgot-password/verify-otp', { email, otp }, 'Invalid or expired code.');
}

export function resetPassword({ email, resetToken, newPassword, confirmPassword }) {
  return postAuthJson(
    '/auth/forgot-password/reset',
    { email, resetToken, newPassword, confirmPassword },
    'Something went wrong. Please try again.',
  );
}

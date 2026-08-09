const ACCESS_TOKEN_KEY = 'cb_access_token';
const REFRESH_TOKEN_KEY = 'cb_refresh_token';
const USER_KEY = 'cb_user';

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

// Register has real firstName/lastName from the form; login's AuthResponse carries no name at all,
// so it stores email only -- getDisplayName falls back to the email's local part in that case.
export function setStoredUser({ firstName, lastName, email }) {
  localStorage.setItem(USER_KEY, JSON.stringify({ firstName: firstName || '', lastName: lastName || '', email: email || '' }));
}

export function getDisplayName(fallbackLabel) {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY) || '{}');
    const full = `${u.firstName || ''} ${u.lastName || ''}`.trim();
    if (full) return full;
    if (u.email) return u.email.split('@')[0];
  } catch {
    // fall through to the label
  }
  return fallbackLabel;
}

export function isTokenValid(token) {
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return !payload.exp || payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

// Decodes the JWT claims for UI gating only (e.g. hiding the admin route) -- never trust this as auth.
export function getTokenPayload() {
  const token = getAccessToken();
  if (!token) return null;
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
  } catch {
    return null;
  }
}

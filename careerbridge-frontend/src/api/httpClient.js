import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please log in again.';

// Every 401 past the gateway means the access token is missing, invalid, or expired -- the
// gateway is the only thing that issues 401, and it does so for token problems only. So on any
// 401 the right first move is always the same: try a silent refresh, then retry once.
let refreshPromise = null;

function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.reject(new Error('No refresh token'));

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) throw new Error('Refresh failed');
        const data = await res.json();
        setTokens(data.accessToken, data.refreshToken);
        return data.accessToken;
      })
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

function redirectToLogin() {
  clearTokens();
  if (window.location.pathname !== '/login') window.location.href = '/login';
}

// fallbackMessage: shown when the error response carries no message of its own -- lets each
// caller keep its own specific wording ('Could not build the roadmap.') instead of the generic
// default. Every thrown error carries .status, so a caller that wants special handling for one
// status (404-as-null being the common case) can catch and check e.status rather than this
// client needing to know about it.
export async function authedFetch(path, { fallbackMessage, ...options } = {}) {
  const doFetch = (token) => fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });

  let res = await doFetch(getAccessToken());

  if (res.status === 401) {
    try {
      const newToken = await refreshAccessToken();
      res = await doFetch(newToken);
    } catch {
      redirectToLogin();
      throw new Error(SESSION_EXPIRED_MESSAGE);
    }
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const err = new Error(body.message || body.error || fallbackMessage || 'Something went wrong. Please try again.');
    err.status = res.status;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

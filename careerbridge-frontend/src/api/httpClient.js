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

export async function authedFetch(path, options = {}) {
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
    throw new Error(body.message || body.error || 'Something went wrong. Please try again.');
  }
  return res.status === 204 ? null : res.json();
}

import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Fails soft to null -- a blank widget beats the whole dashboard erroring out.
export async function getMyPrs() {
  try {
    const res = await fetch(`${API_BASE}/prs/my`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` },
    });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

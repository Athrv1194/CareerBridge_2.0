import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Metadata only (no PDF bytes) -- that's what GET /my returns by design on the backend.
export async function getMyResumes() {
  const res = await fetch(`${API_BASE}/resume/my`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!res.ok) return [];
  return res.json();
}

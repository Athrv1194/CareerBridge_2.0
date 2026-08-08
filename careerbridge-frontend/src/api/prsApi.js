import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Every student gets a PRS row the moment they register (prs-service creates it defensively off
// student.registered), so a missing row is not a normal state -- but this still fails soft to null
// rather than throwing, since a dashboard widget going blank beats the whole page erroring out.
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

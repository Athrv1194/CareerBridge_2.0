import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// [] means "no roadmap yet"; a list where every entry has resources: [] means the roadmap exists
// but the catalog hasn't been refreshed for this career. Both are legitimate, non-error states, so
// this never throws for either -- only a genuine network/5xx failure returns [].
export async function getMyResources() {
  try {
    const res = await fetch(`${API_BASE}/ai-coach/resources`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` },
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

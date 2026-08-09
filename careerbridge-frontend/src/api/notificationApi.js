import { getAccessToken } from '../utils/tokenUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

function authHeaders() {
  return { Authorization: `Bearer ${getAccessToken()}` };
}

// Empty list, not an error, when the student has none yet.
export async function getMyNotifications() {
  const res = await fetch(`${API_BASE}/notification/my`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load your notifications.');
  return res.json();
}

export async function getUnreadCount() {
  const res = await fetch(`${API_BASE}/notification/unread-count`, { headers: authHeaders() });
  if (!res.ok) throw new Error('Could not load your unread count.');
  return res.json();
}

export async function markNotificationRead(notificationId) {
  const res = await fetch(`${API_BASE}/notification/${notificationId}/read`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!res.ok) throw new Error('Could not mark that notification as read.');
  return res.json();
}

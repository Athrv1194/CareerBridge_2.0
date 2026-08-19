import { authedFetch } from './httpClient';

// Empty list, not an error, when the student has none yet.
export function getMyNotifications() {
  return authedFetch('/notification/my', { fallbackMessage: 'Could not load your notifications.' });
}

export function getUnreadCount() {
  return authedFetch('/notification/unread-count', { fallbackMessage: 'Could not load your unread count.' });
}

export function markNotificationRead(notificationId) {
  return authedFetch(`/notification/${notificationId}/read`, {
    method: 'PATCH',
    fallbackMessage: 'Could not mark that notification as read.',
  });
}

import { authedFetch } from './httpClient';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// Public endpoint, no auth needed.
export async function getPlans() {
  const res = await fetch(`${API_BASE}/payment/plans`);
  if (!res.ok) return [];
  return res.json();
}

export function createOrder(planId) {
  return authedFetch('/payment/orders', { method: 'POST', body: JSON.stringify({ planId }) });
}

export function verifyPayment(payload) {
  return authedFetch('/payment/verify', { method: 'POST', body: JSON.stringify(payload) });
}

export function getMySubscription() {
  return authedFetch('/payment/subscription/my');
}

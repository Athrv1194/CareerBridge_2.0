const NAV_COLLAPSED_KEY = 'cb_nav_collapsed';

export function getNavCollapsed() {
  return localStorage.getItem(NAV_COLLAPSED_KEY) === 'true';
}

export function setNavCollapsed(collapsed) {
  localStorage.setItem(NAV_COLLAPSED_KEY, String(collapsed));
}

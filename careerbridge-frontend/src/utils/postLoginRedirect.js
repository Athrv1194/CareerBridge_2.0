import { getMyRecommendation } from '../api/recommendationApi';
import { getMyProfile } from '../api/studentApi';

// Exported so any logged-in-state UI (e.g. HomePage's header CTA) can point a non-student
// straight at their own console instead of a generic /dashboard, without duplicating this map.
export const ROLE_REDIRECTS = {
  RECRUITER: '/recruiter-console',
  ORG_ADMIN: '/college-dashboard',
  PLACEMENT_OFFICER: '/placement-console',
  SUPER_ADMIN: '/super-admin',
  MENTOR: '/dashboard',
};

// Only students walk the onboarding -> assessment -> roadmap chain; other roles go straight to their console.
export async function resolvePostLoginDestination(role) {
  if (role !== 'STUDENT') {
    return ROLE_REDIRECTS[role] || '/dashboard';
  }

  try {
    const profile = await getMyProfile();
    const hasOnboarded = (profile?.educations?.length || 0) > 0 && (profile?.skills?.length || 0) > 0;
    if (!hasOnboarded) return '/onboarding';

    const recommendation = await getMyRecommendation();
    if (!recommendation) return '/assessment';

    return '/dashboard';
  } catch {
    // A downstream hiccup shouldn't block login -- onboarding is a safe fallback either way.
    return '/onboarding';
  }
}

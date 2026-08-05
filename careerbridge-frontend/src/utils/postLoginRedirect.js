import { getMyRecommendation } from '../api/recommendationApi';
import { getMyProfile } from '../api/studentApi';

const ROLE_REDIRECTS = {
  RECRUITER: '/recruiter-console',
  ORG_ADMIN: '/college-dashboard',
  PLACEMENT_OFFICER: '/college-dashboard',
  SUPER_ADMIN: '/dashboard',
  MENTOR: '/dashboard',
};

/**
 * Only a STUDENT goes through onboarding -> assessment -> roadmap/dashboard; every other role
 * lands on its own console with no "where did they leave off" question to answer, so this never
 * runs for them.
 *
 * A dashboard with no assessment, no recommendation and no roadmap has nothing to show -- so a
 * student is sent to wherever that chain is actually incomplete, not to a page that would just be
 * empty. No education/skills on the profile means onboarding was never finished; a profile with
 * no recommendation yet means the assessment hasn't been completed (recommendation-service only
 * ever creates one by consuming assessment.completed). Only once both exist does /dashboard mean
 * anything.
 */
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
    // Any of these calls failing (a downstream service down, a fresh profile still being
    // created) must not block login itself -- falling back to onboarding is the safe default,
    // since it is harmless to show even to a fully-onboarded student.
    return '/onboarding';
  }
}

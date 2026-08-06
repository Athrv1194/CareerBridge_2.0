import { Logo } from '../../components/ui';

/**
 * Minimal placeholder. postLoginRedirect.js already sends ORG_ADMIN and PLACEMENT_OFFICER here on
 * login -- without this route their first login renders a blank page. Full dashboard (PRS
 * leaderboard, org stats, applications) is future scope; this branch only opens the provisioning
 * path that gets a TPO an ORG_ADMIN account in the first place.
 */
export default function CollegeDashboardPage() {
  return (
    <div
      style={{
        minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center',
        justifyContent: 'center', gap: 16, background: 'var(--bone-100)', fontFamily: 'var(--font-sans)',
        padding: 24, textAlign: 'center',
      }}
    >
      <Logo size={40} />
      <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
        Welcome to your college dashboard
      </h1>
      <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0, maxWidth: 420 }}>
        Your institution admin account is active. The full dashboard &mdash; PRS leaderboard,
        placement stats and applications &mdash; is coming soon.
      </p>
    </div>
  );
}

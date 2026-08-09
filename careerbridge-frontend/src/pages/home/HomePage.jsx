import { useEffect, useRef, useState } from 'react';
import { useAuthState } from '../../hooks/useAuth';
import {
  Button, IconButton, Logo, StatTile, ProgressMeter, ScoreRing, ListRow, Badge, Icon,
} from '../../components/ui';
import './home.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

const NAV_LINKS = [
  { href: '#features', label: 'Features' },
  { href: '#how-it-works', label: 'Roadmap' },
  { href: '/register-institution', label: 'For colleges' },
  { href: '/recruiter-console', label: 'For recruiters' },
];

const HOW_IT_WORKS = [
  { num: '01', title: 'Take the assessment', body: 'Three categories: aptitude, domain knowledge and soft skills. 20 questions, about 18 minutes. Your answers rank 7 career paths.' },
  { num: '02', title: 'See your ranked matches', body: 'Every career is scored against your actual answers — not your degree, not your title.' },
  { num: '03', title: 'Follow your roadmap', body: 'Nine milestones, ordered by impact. Completing one moves your readiness score within the hour.' },
];

const SCORE_BREAKDOWN = [
  { label: 'Assessment', pct: 40, note: 'Scored from your latest attempt' },
  { label: 'Roadmap', pct: 30, note: 'Milestone completion' },
  { label: 'Profile depth', pct: 20, note: 'Education, skills, projects, résumé URL' },
  { label: 'Résumé', pct: 10, note: 'Generated and scanned in-app' },
];

const DEFAULT_PLANS = [
  { planName: 'Free', priceDisplay: '$0', billingCycle: 'Forever' },
  { planName: 'Student premium', priceDisplay: '$9', billingCycle: 'Per month' },
  { planName: 'College basic', priceDisplay: '$199', billingCycle: 'Per month' },
];

function useReveal() {
  const ref = useRef(null);
  const [revealed, setRevealed] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const obs = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setRevealed(true);
        obs.disconnect();
      }
    }, { threshold: 0.15 });
    obs.observe(el);
    return () => obs.disconnect();
  }, []);
  return [ref, {
    opacity: revealed ? 1 : 0,
    transform: `translateY(${revealed ? 0 : 18}px)`,
    transition: 'opacity 700ms cubic-bezier(.2,0,.2,1), transform 700ms cubic-bezier(.2,0,.2,1)',
  }];
}

function useAnimatedStats() {
  const ref = useRef(null);
  const animated = useRef(false);
  const [counts, setCounts] = useState({ students: 0, careers: 0, colleges: 0, readiness: 0 });
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const obs = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting || animated.current) return;
      animated.current = true;
      const targets = { students: 25000, careers: 1200, colleges: 850, readiness: 92 };
      const start = performance.now();
      const duration = 900;
      const tick = (now) => {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - (1 - t) ** 3;
        setCounts({
          students: Math.round(targets.students * eased),
          careers: Math.round(targets.careers * eased),
          colleges: Math.round(targets.colleges * eased),
          readiness: Math.round(targets.readiness * eased),
        });
        if (t < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
      obs.disconnect();
    }, { threshold: 0.3 });
    obs.observe(el);
    return () => obs.disconnect();
  }, []);
  return [ref, counts];
}

export default function HomePage() {
  const { loggedIn, logout } = useAuthState();
  const [isMobile, setIsMobile] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [heroIn, setHeroIn] = useState(false);
  const [plans, setPlans] = useState(DEFAULT_PLANS);

  const [statsRef, statCounts] = useAnimatedStats();
  const [r1, s1] = useReveal();
  const [r2, s2] = useReveal();
  const [r3, s3] = useReveal();
  const [r4, s4] = useReveal();

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 1180px)');
    const update = () => setIsMobile(mq.matches);
    update();
    mq.addEventListener('change', update);
    return () => mq.removeEventListener('change', update);
  }, []);

  useEffect(() => {
    const id = requestAnimationFrame(() => setHeroIn(true));
    return () => cancelAnimationFrame(id);
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetch(`${API_BASE}/payment/plans`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (cancelled || !data) return;
        const wanted = ['FREE', 'STUDENT_PREMIUM', 'COLLEGE_BASIC'];
        const picked = wanted
          .map((name) => data.find((p) => p.planName === name))
          .filter(Boolean)
          .slice(0, 3)
          .map((p) => ({
            planName: (p.planName || '').replace(/_/g, ' ').toLowerCase(),
            priceDisplay: `${p.currency || ''}${p.price}`.trim(),
            billingCycle: p.billingCycle || '',
          }));
        if (picked.length) setPlans(picked);
      })
      .catch(() => { /* API not reachable — keep placeholder plans */ });
    return () => { cancelled = true; };
  }, []);

  const decoratedPlans = plans.map((p, i) => {
    const featured = plans.length === 3 && i === 1;
    const isThirdTier = plans.length === 3 && i === 2;
    return {
      ...p,
      featured,
      cardBg: featured ? 'var(--ink-900)' : isThirdTier ? 'var(--taupe-100)' : 'var(--bone-50)',
      textColor: featured ? 'var(--bone-50)' : 'var(--ink-900)',
      mutedColor: featured ? 'var(--ink-400)' : 'var(--ink-500)',
      buttonVariant: featured ? 'quiet' : 'secondary',
    };
  });

  const closeDrawer = () => setDrawerOpen(false);

  return (
    <div style={{ background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)', minHeight: '100vh' }}>
      <header style={{ position: 'sticky', top: 0, zIndex: 30, background: 'var(--bone-100)', borderBottom: 'var(--border-hairline)', height: 'var(--header-height)' }}>
        <div style={{ width: '100%', boxSizing: 'border-box', padding: '0 32px', height: 64, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 24 }}>
          <Logo size={36} />

          {!isMobile && (
            <nav style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 22, flex: 1, minWidth: 0, overflow: 'hidden' }}>
              {NAV_LINKS.map((link) => (
                <a key={link.href} href={link.href} className="cb-home-nav-link" style={{ fontSize: 13, fontWeight: 400, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-700)', whiteSpace: 'nowrap', flexShrink: 0 }}>
                  {link.label}
                </a>
              ))}
            </nav>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexShrink: 0 }}>
            {isMobile && (
              <IconButton icon={drawerOpen ? 'x' : 'sliders-horizontal'} label="Menu" onClick={() => setDrawerOpen((v) => !v)} />
            )}
            {!isMobile && (
              <div style={{ display: 'flex', flexShrink: 0, gap: 14 }}>
                {loggedIn ? (
                  <>
                    <Button variant="ghost" size="sm" onClick={logout}>Log out</Button>
                    <Button variant="primary" size="sm" iconAfter="arrow-right" to="/dashboard">Dashboard</Button>
                  </>
                ) : (
                  <>
                    <Button variant="ghost" size="sm" to="/login">Log in</Button>
                    <Button variant="primary" size="sm" iconAfter="arrow-right" to="/register">Create account</Button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>

        {drawerOpen && (
          <div style={{ background: 'var(--bone-50)', borderBottom: 'var(--border-hairline)', padding: '20px 32px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
            {NAV_LINKS.map((link) => (
              <a key={link.href} href={link.href} onClick={closeDrawer} className="cb-home-nav-link" style={{ fontSize: 13, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-700)', width: 'fit-content' }}>
                {link.label}
              </a>
            ))}
            <div style={{ height: 1, background: 'var(--line-hairline)', margin: '4px 0' }} />
            {loggedIn ? (
              <>
                <Button variant="primary" iconAfter="arrow-right" to="/dashboard" fullWidth>Dashboard</Button>
                <Button variant="ghost" onClick={logout} fullWidth>Log out</Button>
              </>
            ) : (
              <>
                <Button variant="primary" iconAfter="arrow-right" to="/register" fullWidth>Create account</Button>
                <Button variant="ghost" to="/login" fullWidth>Log in</Button>
              </>
            )}
          </div>
        )}
      </header>

      <section style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', alignItems: 'stretch', background: 'var(--bone-100)' }}>
        <div
          style={{
            opacity: heroIn ? 1 : 0,
            transform: `translateY(${heroIn ? 0 : 12}px)`,
            transition: 'opacity 700ms cubic-bezier(.2,0,.2,1), transform 700ms cubic-bezier(.2,0,.2,1)',
            display: 'flex', flexDirection: 'column', justifyContent: 'center',
            padding: '88px 48px 88px max(32px, calc((100vw - 1160px) / 2))',
          }}
        >
          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>AI-powered career platform</span>
          <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1.02, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: '18px 0 0', fontWeight: 400 }}>
            Build a career that <i>fits</i> you.
          </h1>
          <p style={{ fontSize: 16, lineHeight: 1.6, color: 'var(--ink-600)', maxWidth: 520, margin: '22px 0 0', fontWeight: 400 }}>
            Discover your strengths, explore the right opportunities, and follow a personalised path to placement success — scored, sequenced and kept current.
          </p>
          <div style={{ display: 'flex', gap: 12, marginTop: 32, flexWrap: 'wrap' }}>
            <Button size="lg" iconAfter="arrow-right" to="/register">Start my assessment</Button>
            <Button size="lg" variant="secondary" to="/careers">Explore careers</Button>
          </div>
        </div>
        <div style={{ position: 'relative', overflow: 'hidden', minHeight: 720, background: 'var(--bone-200)' }}>
          <img src="/images/hero-01.jpg" alt="" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center top', filter: 'saturate(.92)' }} />
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(90deg, var(--bone-100) 0%, rgba(245,243,239,0) 22%)' }} />
        </div>
      </section>

      <section ref={statsRef} style={{ background: 'var(--ink-900)' }}>
        <div style={{ maxWidth: 'var(--max-content)', margin: '0 auto', padding: '0 32px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)' }}>
            <StatTile tone="inverse" value={`${statCounts.students.toLocaleString()}+`} label="Students guided" />
            <StatTile tone="inverse" value={`${statCounts.careers.toLocaleString()}+`} label="Career paths" />
            <StatTile tone="inverse" value={`${statCounts.colleges.toLocaleString()}+`} label="Colleges partnered" />
            <StatTile tone="inverse" value={`${statCounts.readiness}%`} label="Median readiness" />
          </div>
        </div>
      </section>

      <div id="features" />
      <section id="how-it-works" ref={r1} style={{ ...s1, maxWidth: 'var(--max-content)', margin: '0 auto', padding: '96px 32px 0' }}>
        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>How it works</span>
        <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 44, lineHeight: 1.08, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: '24px 0 0', fontWeight: 400, maxWidth: 660 }}>
          Assessment in, roadmap out.
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', marginTop: 40 }}>
          {HOW_IT_WORKS.map((step) => (
            <div key={step.num} style={{ background: 'var(--bone-50)', padding: '32px 28px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              <span className="cb-num" style={{ fontSize: 12, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>{step.num}</span>
              <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)', margin: 0 }}>{step.title}</h3>
              <p style={{ fontSize: 14, lineHeight: 1.65, color: 'var(--ink-600)', margin: 0 }}>{step.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section ref={r2} style={{ ...s2, maxWidth: 'var(--max-content)', margin: '0 auto', padding: '96px 32px 0' }}>
        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Your readiness score</span>
        <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.12, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: '24px 0 0', fontWeight: 400, maxWidth: 660 }}>
          One number. Four inputs. Updated by actions, not forms.
        </h2>

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,.8fr) minmax(0,1.2fr)', gap: 1, background: 'var(--line-hairline)', marginTop: 40 }}>
          <div style={{ background: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '48px 32px' }}>
            <ScoreRing value={82} grade="B" size="lg" label="Placement readiness" caption="Résumé and profile are strong. Assessment coverage is the gap." />
          </div>
          <div style={{ background: 'var(--bone-50)', padding: '44px 40px', display: 'flex', flexDirection: 'column', gap: 26 }}>
            {SCORE_BREAKDOWN.map((row) => (
              <div key={row.label} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                  <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>{row.label}</span>
                  <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 22, color: 'var(--ink-900)' }}>{row.pct}%</span>
                </div>
                <ProgressMeter value={row.pct} max={100} tone="accent" />
                <span style={{ fontSize: 13, color: 'var(--ink-500)' }}>{row.note}</span>
              </div>
            ))}
          </div>
        </div>

        <p style={{ fontSize: 13, fontStyle: 'italic', color: 'var(--ink-400)', margin: '20px 0 0' }}>
          Readiness is derived from events, never self-reported. Completing a milestone moves it within the hour.
        </p>
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 36 }}>
          <Button size="lg" iconAfter="arrow-right" to="/register">Start my assessment</Button>
        </div>
      </section>

      <section ref={r3} style={{ ...s3, background: 'var(--bone-100)', marginTop: 96 }}>
        <div style={{ maxWidth: 'var(--max-content)', margin: '0 auto', padding: '0 32px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', background: 'var(--line-hairline)', gap: 1 }}>
            <div style={{ background: 'var(--bone-50)', padding: '44px 28px', display: 'flex', flexDirection: 'column', gap: 20 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>For colleges /</span>
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 28, lineHeight: 1.15, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Track your cohort&apos;s readiness, not just their placements.
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', borderTop: 'var(--border-hairline)', width: '100%' }}>
                <ListRow leading={<Icon name="chart-no-axes-column" />} title="PRS leaderboard scoped to your institution" />
                <ListRow leading={<Icon name="building-2" />} title="Department-level readiness breakdown" />
                <ListRow leading={<Icon name="triangle-alert" />} title="At-risk student list, updated daily" />
              </div>
              <Button variant="secondary" iconAfter="arrow-right" to="/register-institution" style={{ marginTop: 8, alignSelf: 'flex-start' }}>Register your institution</Button>
            </div>
            <div style={{ background: 'var(--bone-50)', padding: '44px 28px', display: 'flex', flexDirection: 'column', gap: 20 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>For recruiters /</span>
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 28, lineHeight: 1.15, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Filter 1,240 candidates by readiness score, not résumé.
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', borderTop: 'var(--border-hairline)', width: '100%' }}>
                <ListRow leading={<Icon name="sliders-horizontal" />} title="Candidate pool filtered by skill and PRS grade" />
                <ListRow leading={<Icon name="route" />} title="Application pipeline — shortlist, interview, offer in one view" />
                <ListRow leading={<Icon name="download" />} title="Placement stats exported in one click" />
              </div>
              <Button variant="secondary" iconAfter="arrow-right" to="/register?role=RECRUITER" style={{ marginTop: 8, alignSelf: 'flex-start' }}>Post a role</Button>
            </div>
          </div>
        </div>
      </section>

      <section ref={r4} style={{ ...s4, maxWidth: 'var(--max-content)', margin: '96px auto 0', padding: '0 32px' }}>
        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Plans</span>
        <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.12, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: '24px 0 0', fontWeight: 400 }}>
          Start free. Scale when it works.
        </h2>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,minmax(0,1fr))', gap: 20, marginTop: 40 }}>
          {decoratedPlans.map((plan) => (
            <div
              key={plan.planName}
              style={{
                background: plan.cardBg, padding: '36px 30px', display: 'flex', flexDirection: 'column',
                gap: 16, position: 'relative', borderRadius: 'var(--radius-md)',
                border: plan.featured ? 'none' : 'var(--border-hairline)',
              }}
            >
              {plan.featured && <Badge tone="inverse">Most popular</Badge>}
              {plan.featured ? (
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: plan.mutedColor }}>{plan.planName}</span>
              ) : (
                <Badge tone="dark">{plan.planName}</Badge>
              )}
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 40, color: plan.textColor, fontWeight: 400 }}>{plan.priceDisplay}</span>
              </div>
              <span style={{ fontSize: 13, color: plan.mutedColor }}>{plan.billingCycle}</span>
              <Button variant={plan.buttonVariant} iconAfter="arrow-right" to="/register" style={{ marginTop: 8, alignSelf: 'flex-start' }}>Get started</Button>
            </div>
          ))}
        </div>
      </section>

      <footer style={{ marginTop: 96, background: 'var(--bone-200)', color: 'var(--ink-800)', borderTop: 'var(--border-hairline)' }}>
        <div style={{ maxWidth: 'var(--max-content)', margin: '0 auto', padding: '64px 32px 40px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)' }}>
            <div style={{ background: 'var(--bone-200)', padding: '0 24px 0 0', display: 'flex', flexDirection: 'column', gap: 14 }}>
              <Logo size={22} />
              <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Bridge today. Build tomorrow.</span>
            </div>
            <div style={{ background: 'var(--bone-200)', padding: '0 24px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Product</span>
              <a href="#features" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Features</a>
              <a href="#how-it-works" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Roadmap</a>
              <a href="/opportunities" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Opportunities</a>
              <a href="/resume" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Résumé builder</a>
            </div>
            <div style={{ background: 'var(--bone-200)', padding: '0 24px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Company</span>
              <a href="/about" style={{ fontSize: 13, color: 'var(--ink-700)' }}>About</a>
              <a href="/register-institution" style={{ fontSize: 13, color: 'var(--ink-700)' }}>For colleges</a>
              <a href="/recruiter-console" style={{ fontSize: 13, color: 'var(--ink-700)' }}>For recruiters</a>
              <a href="/pricing" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Pricing</a>
            </div>
            <div style={{ background: 'var(--bone-200)', padding: '0 0 0 24px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Legal</span>
              <a href="/terms" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Terms of service</a>
              <a href="/privacy" style={{ fontSize: 13, color: 'var(--ink-700)' }}>Privacy policy</a>
            </div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 40, paddingTop: 24, borderTop: 'var(--border-hairline)', fontSize: 12, color: 'var(--ink-400)' }}>
            <span>© 2026 CareerBridge</span>
            <span>Built on Spring Boot microservices</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
